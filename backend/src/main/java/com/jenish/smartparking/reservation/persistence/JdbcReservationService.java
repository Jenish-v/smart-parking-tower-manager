package com.jenish.smartparking.reservation.persistence;

import com.jenish.smartparking.allocation.domain.VehicleIdentifier;
import com.jenish.smartparking.facility.domain.FacilityId;
import com.jenish.smartparking.facility.domain.FacilityNotFoundException;
import com.jenish.smartparking.facility.domain.SizeClass;
import com.jenish.smartparking.reservation.application.OverlappingVehicleReservationException;
import com.jenish.smartparking.reservation.application.ReservationCapacityExceededException;
import com.jenish.smartparking.reservation.application.ReservationIdentifierConflictException;
import com.jenish.smartparking.reservation.application.ReservationNotFoundException;
import com.jenish.smartparking.reservation.application.ReservationService;
import com.jenish.smartparking.reservation.domain.Reservation;
import com.jenish.smartparking.reservation.domain.ReservationId;
import com.jenish.smartparking.reservation.domain.ReservationStatus;
import com.jenish.smartparking.reservation.domain.ReservationWindow;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Lazy
public final class JdbcReservationService implements ReservationService {

    private static final String RESERVATION_COLUMNS = """
            SELECT r.id,
                   r.facility_id,
                   r.vehicle_identifier,
                   r.required_size,
                   r.starts_at,
                   r.ends_at,
                   r.created_at,
                   r.status,
                   r.resolved_at
            FROM reservations r
            """;

    private final JdbcClient jdbcClient;

    private final TransactionOperations transactions;

    private final Clock clock;

    @Autowired
    public JdbcReservationService(
            JdbcClient jdbcClient,
            PlatformTransactionManager transactionManager) {
        this(jdbcClient, new TransactionTemplate(transactionManager), Clock.systemUTC());
    }

    JdbcReservationService(
            JdbcClient jdbcClient,
            TransactionOperations transactions,
            Clock clock) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient must not be null");
        this.transactions = Objects.requireNonNull(transactions, "transactions must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public Reservation create(
            ReservationId reservationId,
            FacilityId facilityId,
            VehicleIdentifier vehicleIdentifier,
            SizeClass requiredSize,
            ReservationWindow window) {
        Objects.requireNonNull(reservationId, "reservationId must not be null");
        Objects.requireNonNull(facilityId, "facilityId must not be null");
        Objects.requireNonNull(vehicleIdentifier, "vehicleIdentifier must not be null");
        Objects.requireNonNull(requiredSize, "requiredSize must not be null");
        Objects.requireNonNull(window, "window must not be null");
        return transactions.execute(status -> createOnce(
                reservationId,
                facilityId,
                vehicleIdentifier,
                requiredSize,
                window));
    }

    @Override
    public Reservation cancel(FacilityId facilityId, ReservationId reservationId) {
        Objects.requireNonNull(facilityId, "facilityId must not be null");
        Objects.requireNonNull(reservationId, "reservationId must not be null");
        return transactions.execute(status -> cancelOnce(facilityId, reservationId));
    }

    @Override
    public Optional<Reservation> find(FacilityId facilityId, ReservationId reservationId) {
        Objects.requireNonNull(facilityId, "facilityId must not be null");
        Objects.requireNonNull(reservationId, "reservationId must not be null");
        return transactions.execute(status -> {
            expireDue(facilityId, now());
            return findStored(facilityId, reservationId, false);
        });
    }

    @Override
    public List<Reservation> history(
            FacilityId facilityId,
            VehicleIdentifier vehicleIdentifier) {
        Objects.requireNonNull(facilityId, "facilityId must not be null");
        Objects.requireNonNull(vehicleIdentifier, "vehicleIdentifier must not be null");
        return transactions.execute(status -> {
            expireDue(facilityId, now());
            return jdbcClient.sql(RESERVATION_COLUMNS + """
                    WHERE r.facility_id = :facilityId
                      AND r.vehicle_identifier = :vehicleIdentifier
                    ORDER BY r.created_at DESC, r.id
                    """)
                    .param("facilityId", facilityId.value())
                    .param("vehicleIdentifier", vehicleIdentifier.value())
                    .query(this::mapReservation)
                    .list();
        });
    }

    private Reservation createOnce(
            ReservationId reservationId,
            FacilityId facilityId,
            VehicleIdentifier vehicleIdentifier,
            SizeClass requiredSize,
            ReservationWindow window) {
        Instant createdAt = now();
        lockFacility(facilityId);
        ensureFacilityExists(facilityId);
        expireDue(facilityId, createdAt);
        Optional<Reservation> replay = findById(reservationId);
        if (replay.isPresent()) {
            return replayCreate(
                    replay.orElseThrow(),
                    facilityId,
                    vehicleIdentifier,
                    requiredSize,
                    window);
        }
        Reservation candidate = Reservation.confirm(
                reservationId,
                facilityId,
                vehicleIdentifier,
                requiredSize,
                window,
                createdAt);
        List<Reservation> overlapping = findOverlapping(facilityId, window);
        if (overlapping.stream().anyMatch(existing -> existing.vehicleIdentifier().equals(vehicleIdentifier))) {
            throw new OverlappingVehicleReservationException(vehicleIdentifier);
        }
        if (!hasCapacity(requiredSize, window, overlapping, capacity(facilityId))) {
            throw new ReservationCapacityExceededException(requiredSize, window);
        }
        insert(candidate);
        return candidate;
    }

    private Reservation cancelOnce(FacilityId facilityId, ReservationId reservationId) {
        lockFacility(facilityId);
        Instant cancelledAt = now();
        expireDue(facilityId, cancelledAt);
        Reservation reservation = findStored(facilityId, reservationId, true)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        if (reservation.status() == ReservationStatus.CANCELLED) {
            return reservation;
        }
        Reservation cancelled = reservation.cancel(cancelledAt);
        jdbcClient.sql("""
                UPDATE reservations
                SET status = 'CANCELLED',
                    resolved_at = :resolvedAt
                WHERE id = :reservationId
                  AND status = 'CONFIRMED'
                """)
                .param("resolvedAt", databaseTime(cancelled.resolvedAt()))
                .param("reservationId", reservationId.value())
                .update();
        return cancelled;
    }

    private void lockFacility(FacilityId facilityId) {
        jdbcClient.sql("""
                SELECT pg_advisory_xact_lock(
                    hashtextextended(:facilityId, 1)
                )
                """)
                .param("facilityId", facilityId.value().toString())
                .query((resultSet, rowNumber) -> Boolean.TRUE)
                .single();
    }

    private void ensureFacilityExists(FacilityId facilityId) {
        boolean exists = jdbcClient.sql("SELECT EXISTS (SELECT 1 FROM facilities WHERE id = :facilityId)")
                .param("facilityId", facilityId.value())
                .query(Boolean.class)
                .single();
        if (!exists) {
            throw new FacilityNotFoundException(facilityId);
        }
    }

    private void expireDue(FacilityId facilityId, Instant expiredAt) {
        jdbcClient.sql("""
                UPDATE reservations
                SET status = 'EXPIRED',
                    resolved_at = :expiredAt
                WHERE facility_id = :facilityId
                  AND status = 'CONFIRMED'
                  AND ends_at <= :expiredAt
                """)
                .param("expiredAt", databaseTime(expiredAt))
                .param("facilityId", facilityId.value())
                .update();
    }

    private List<Reservation> findOverlapping(
            FacilityId facilityId,
            ReservationWindow window) {
        return jdbcClient.sql(RESERVATION_COLUMNS + """
                WHERE r.facility_id = :facilityId
                  AND r.status = 'CONFIRMED'
                  AND r.starts_at < :endsAt
                  AND r.ends_at > :startsAt
                ORDER BY r.starts_at, r.ends_at, r.id
                """)
                .param("facilityId", facilityId.value())
                .param("startsAt", databaseTime(window.startsAt()))
                .param("endsAt", databaseTime(window.endsAt()))
                .query(this::mapReservation)
                .list();
    }

    private Map<SizeClass, Integer> capacity(FacilityId facilityId) {
        Map<SizeClass, Integer> capacity = new EnumMap<>(SizeClass.class);
        for (SizeClass sizeClass : SizeClass.values()) {
            capacity.put(sizeClass, 0);
        }
        jdbcClient.sql("""
                SELECT ps.size_class, count(*) AS space_count
                FROM parking_spaces ps
                JOIN parking_zones pz ON pz.id = ps.zone_id
                JOIN parking_floors pf ON pf.id = pz.floor_id
                WHERE pf.facility_id = :facilityId
                  AND ps.operational_state = 'ACTIVE'
                GROUP BY ps.size_class
                """)
                .param("facilityId", facilityId.value())
                .query((resultSet, rowNumber) -> new SizeCapacity(
                        SizeClass.valueOf(resultSet.getString("size_class")),
                        resultSet.getInt("space_count")))
                .list()
                .forEach(item -> capacity.put(item.sizeClass(), item.count()));
        return capacity;
    }

    private boolean hasCapacity(
            SizeClass candidateSize,
            ReservationWindow candidateWindow,
            List<Reservation> overlapping,
            Map<SizeClass, Integer> capacity) {
        int allSpaces = capacity.values().stream().mapToInt(Integer::intValue).sum();
        int mediumAndLarge = capacity.get(SizeClass.MEDIUM) + capacity.get(SizeClass.LARGE);
        int large = capacity.get(SizeClass.LARGE);
        return fitsPool(candidateSize, SizeClass.SMALL, candidateWindow, overlapping, allSpaces)
                && fitsPool(candidateSize, SizeClass.MEDIUM, candidateWindow, overlapping, mediumAndLarge)
                && fitsPool(candidateSize, SizeClass.LARGE, candidateWindow, overlapping, large);
    }

    private boolean fitsPool(
            SizeClass candidateSize,
            SizeClass minimumRequiredSize,
            ReservationWindow candidateWindow,
            List<Reservation> overlapping,
            int poolCapacity) {
        if (!candidateSize.canAccommodate(minimumRequiredSize)) {
            return true;
        }
        List<CapacityEvent> events = new ArrayList<>();
        overlapping.stream()
                .filter(reservation -> reservation.requiredSize().canAccommodate(minimumRequiredSize))
                .forEach(reservation -> {
                    Instant startsAt = later(reservation.window().startsAt(), candidateWindow.startsAt());
                    Instant endsAt = earlier(reservation.window().endsAt(), candidateWindow.endsAt());
                    events.add(new CapacityEvent(startsAt, 1));
                    events.add(new CapacityEvent(endsAt, -1));
                });
        events.sort(Comparator
                .comparing(CapacityEvent::at)
                .thenComparingInt(CapacityEvent::change));
        int concurrent = 0;
        int maximum = 0;
        for (CapacityEvent event : events) {
            concurrent += event.change();
            maximum = Math.max(maximum, concurrent);
        }
        return maximum + 1 <= poolCapacity;
    }

    private Optional<Reservation> findStored(
            FacilityId facilityId,
            ReservationId reservationId,
            boolean lock) {
        String sql = RESERVATION_COLUMNS + """
                WHERE r.facility_id = :facilityId
                  AND r.id = :reservationId
                """ + (lock ? " FOR UPDATE OF r" : "");
        return jdbcClient.sql(sql)
                .param("facilityId", facilityId.value())
                .param("reservationId", reservationId.value())
                .query(this::mapReservation)
                .optional();
    }

    private Optional<Reservation> findById(ReservationId reservationId) {
        return jdbcClient.sql(RESERVATION_COLUMNS + " WHERE r.id = :reservationId")
                .param("reservationId", reservationId.value())
                .query(this::mapReservation)
                .optional();
    }

    private Reservation replayCreate(
            Reservation stored,
            FacilityId facilityId,
            VehicleIdentifier vehicleIdentifier,
            SizeClass requiredSize,
            ReservationWindow window) {
        if (!stored.facilityId().equals(facilityId)
                || !stored.vehicleIdentifier().equals(vehicleIdentifier)
                || stored.requiredSize() != requiredSize
                || !stored.window().equals(window)) {
            throw new ReservationIdentifierConflictException(stored.id());
        }
        return stored;
    }

    private void insert(Reservation reservation) {
        jdbcClient.sql("""
                INSERT INTO reservations (
                    id,
                    facility_id,
                    vehicle_identifier,
                    required_size,
                    starts_at,
                    ends_at,
                    created_at,
                    status
                ) VALUES (
                    :id,
                    :facilityId,
                    :vehicleIdentifier,
                    :requiredSize,
                    :startsAt,
                    :endsAt,
                    :createdAt,
                    :status
                )
                """)
                .param("id", reservation.id().value())
                .param("facilityId", reservation.facilityId().value())
                .param("vehicleIdentifier", reservation.vehicleIdentifier().value())
                .param("requiredSize", reservation.requiredSize().name())
                .param("startsAt", databaseTime(reservation.window().startsAt()))
                .param("endsAt", databaseTime(reservation.window().endsAt()))
                .param("createdAt", databaseTime(reservation.createdAt()))
                .param("status", reservation.status().name())
                .update();
    }

    private Reservation mapReservation(ResultSet resultSet, int rowNumber) throws SQLException {
        OffsetDateTime resolvedAt = resultSet.getObject("resolved_at", OffsetDateTime.class);
        return new Reservation(
                new ReservationId(resultSet.getObject("id", UUID.class)),
                new FacilityId(resultSet.getObject("facility_id", UUID.class)),
                new VehicleIdentifier(resultSet.getString("vehicle_identifier")),
                SizeClass.valueOf(resultSet.getString("required_size")),
                new ReservationWindow(
                        resultSet.getObject("starts_at", OffsetDateTime.class).toInstant(),
                        resultSet.getObject("ends_at", OffsetDateTime.class).toInstant()),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
                ReservationStatus.valueOf(resultSet.getString("status")),
                resolvedAt == null ? null : resolvedAt.toInstant());
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private static OffsetDateTime databaseTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant later(Instant first, Instant second) {
        return first.isAfter(second) ? first : second;
    }

    private static Instant earlier(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    private record SizeCapacity(SizeClass sizeClass, int count) {
    }

    private record CapacityEvent(Instant at, int change) {
    }
}
