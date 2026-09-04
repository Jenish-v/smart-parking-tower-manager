package com.jenish.smartparking.parkingsession.persistence;

import com.jenish.smartparking.allocation.application.AllocationService;
import com.jenish.smartparking.allocation.domain.ParkingAllocation;
import com.jenish.smartparking.allocation.domain.SpaceLocation;
import com.jenish.smartparking.allocation.domain.VehicleAlreadyParkedException;
import com.jenish.smartparking.allocation.domain.VehicleIdentifier;
import com.jenish.smartparking.facility.domain.FacilityId;
import com.jenish.smartparking.facility.domain.FloorNumber;
import com.jenish.smartparking.facility.domain.SizeClass;
import com.jenish.smartparking.facility.domain.SpaceNumber;
import com.jenish.smartparking.facility.domain.ZoneCode;
import com.jenish.smartparking.parkingsession.application.ActiveParkingSessionExistsException;
import com.jenish.smartparking.parkingsession.application.IdempotencyConflictException;
import com.jenish.smartparking.parkingsession.application.NoActiveParkingSessionException;
import com.jenish.smartparking.parkingsession.application.ParkingSessionService;
import com.jenish.smartparking.parkingsession.application.ParkingSessionExit;
import com.jenish.smartparking.parkingsession.domain.ParkingSession;
import com.jenish.smartparking.parkingsession.domain.RequestId;
import com.jenish.smartparking.parkingsession.domain.SessionId;
import com.jenish.smartparking.reservation.application.ReservationService;
import com.jenish.smartparking.reservation.domain.Reservation;
import com.jenish.smartparking.reservation.domain.ReservationId;
import com.jenish.smartparking.pricing.application.PricingService;
import com.jenish.smartparking.pricing.domain.ParkingReceipt;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
public final class JdbcParkingSessionService implements ParkingSessionService {

    private static final String SESSION_COLUMNS = """
            SELECT ps.id,
                   ps.facility_id,
                   ps.vehicle_identifier,
                   ps.required_size,
                   ps.floor_number,
                   ps.zone_code,
                   ps.space_number,
                   ps.entered_at,
                   ps.exited_at,
                   ps.reservation_id
            FROM parking_sessions ps
            """;

    private static final String ACTIVE_SESSION = SESSION_COLUMNS + """
            WHERE ps.facility_id = :facilityId
              AND ps.vehicle_identifier = :vehicleIdentifier
              AND ps.status = 'ACTIVE'
            """;

    private static final String REQUEST_WITH_SESSION = """
            SELECT pr.request_id,
                   pr.operation,
                   pr.facility_id AS request_facility_id,
                   pr.vehicle_identifier AS request_vehicle_identifier,
                   ps.id,
                   ps.facility_id,
                   ps.vehicle_identifier,
                   ps.required_size,
                   ps.floor_number,
                   ps.zone_code,
                   ps.space_number,
                   ps.entered_at,
                   ps.exited_at,
                   ps.reservation_id
            FROM parking_session_requests pr
            JOIN parking_sessions ps ON ps.id = pr.session_id
            WHERE pr.request_id = :requestId
            """;

    private final JdbcClient jdbcClient;

    private final AllocationService allocationService;

    private final ReservationService reservationService;

    private final PricingService pricingService;

    private final TransactionOperations transactions;

    private final Clock clock;

    @Autowired
    public JdbcParkingSessionService(
            JdbcClient jdbcClient,
            AllocationService allocationService,
            ReservationService reservationService,
            PricingService pricingService,
            PlatformTransactionManager transactionManager) {
        this(
                jdbcClient,
                allocationService,
                reservationService,
                pricingService,
                new TransactionTemplate(transactionManager),
                Clock.systemUTC());
    }

    JdbcParkingSessionService(
            JdbcClient jdbcClient,
            AllocationService allocationService,
            ReservationService reservationService,
            PricingService pricingService,
            TransactionOperations transactions,
            Clock clock) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient must not be null");
        this.allocationService = Objects.requireNonNull(allocationService, "allocationService must not be null");
        this.reservationService = Objects.requireNonNull(reservationService, "reservationService must not be null");
        this.pricingService = Objects.requireNonNull(pricingService, "pricingService must not be null");
        this.transactions = Objects.requireNonNull(transactions, "transactions must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public ParkingSession enter(
            RequestId requestId,
            FacilityId facilityId,
            VehicleIdentifier vehicleIdentifier,
            SizeClass requiredSize) {
        requireCommandArguments(requestId, facilityId, vehicleIdentifier);
        Objects.requireNonNull(requiredSize, "requiredSize must not be null");
        return transactions.execute(status ->
                enterOnce(requestId, facilityId, vehicleIdentifier, requiredSize));
    }

    @Override
    public ParkingSessionExit exit(
            RequestId requestId,
            FacilityId facilityId,
            VehicleIdentifier vehicleIdentifier) {
        requireCommandArguments(requestId, facilityId, vehicleIdentifier);
        return transactions.execute(status ->
                exitOnce(requestId, facilityId, vehicleIdentifier));
    }

    @Override
    public Optional<ParkingSession> findActive(
            FacilityId facilityId,
            VehicleIdentifier vehicleIdentifier) {
        requireLookupArguments(facilityId, vehicleIdentifier);
        return findActiveSession(facilityId, vehicleIdentifier, false);
    }

    @Override
    public List<ParkingSession> history(
            FacilityId facilityId,
            VehicleIdentifier vehicleIdentifier) {
        requireLookupArguments(facilityId, vehicleIdentifier);
        return jdbcClient.sql(SESSION_COLUMNS + """
                WHERE ps.facility_id = :facilityId
                  AND ps.vehicle_identifier = :vehicleIdentifier
                ORDER BY ps.entered_at DESC, ps.id
                """)
                .param("facilityId", facilityId.value())
                .param("vehicleIdentifier", vehicleIdentifier.value())
                .query(this::mapSession)
                .list();
    }

    private ParkingSession enterOnce(
            RequestId requestId,
            FacilityId facilityId,
            VehicleIdentifier vehicleIdentifier,
            SizeClass requiredSize) {
        lockRequest(requestId);
        Optional<StoredRequest> replay = findRequest(requestId);
        if (replay.isPresent()) {
            return replayEnter(replay.orElseThrow(), facilityId, vehicleIdentifier, requiredSize);
        }
        if (findActiveSession(facilityId, vehicleIdentifier, false).isPresent()) {
            throw new ActiveParkingSessionExistsException(vehicleIdentifier);
        }

        Instant enteredAt = now();
        Optional<Reservation> reservation = reservationService.fulfillArrival(
                facilityId,
                vehicleIdentifier,
                requiredSize,
                enteredAt);
        ParkingAllocation allocation;
        try {
            allocation = allocationService.park(facilityId, vehicleIdentifier, requiredSize);
        } catch (VehicleAlreadyParkedException exception) {
            throw new ActiveParkingSessionExistsException(vehicleIdentifier);
        }

        ParkingSession session = ParkingSession.start(
                SessionId.newId(),
                facilityId,
                vehicleIdentifier,
                requiredSize,
                allocation.spaceLocation(),
                enteredAt,
                reservation.map(Reservation::id).orElse(null));
        insertSession(session);
        insertRequest(requestId, Operation.ENTER, session);
        return session;
    }

    private ParkingSessionExit exitOnce(
            RequestId requestId,
            FacilityId facilityId,
            VehicleIdentifier vehicleIdentifier) {
        lockRequest(requestId);
        Optional<StoredRequest> replay = findRequest(requestId);
        if (replay.isPresent()) {
            return replayExit(replay.orElseThrow(), facilityId, vehicleIdentifier);
        }

        ParkingSession active = findActiveSession(facilityId, vehicleIdentifier, true)
                .orElseThrow(() -> new NoActiveParkingSessionException(vehicleIdentifier));
        allocationService.unpark(facilityId, vehicleIdentifier);
        ParkingSession completed = active.complete(now());
        ParkingReceipt receipt = pricingService.assess(
                completed.id().value(),
                completed.requiredSize(),
                completed.enteredAt(),
                completed.exitedAt());
        jdbcClient.sql("""
                UPDATE parking_sessions
                SET status = 'COMPLETED',
                    exited_at = :exitedAt
                WHERE id = :sessionId
                  AND status = 'ACTIVE'
                """)
                .param("exitedAt", databaseTime(completed.exitedAt()))
                .param("sessionId", completed.id().value())
                .update();
        insertRequest(requestId, Operation.EXIT, completed);
        return new ParkingSessionExit(completed, receipt);
    }

    private void lockRequest(RequestId requestId) {
        jdbcClient.sql("""
                SELECT pg_advisory_xact_lock(
                    hashtextextended(:requestId, 0)
                )
                """)
                .param("requestId", requestId.value().toString())
                .query((resultSet, rowNumber) -> Boolean.TRUE)
                .single();
    }

    private Optional<StoredRequest> findRequest(RequestId requestId) {
        return jdbcClient.sql(REQUEST_WITH_SESSION)
                .param("requestId", requestId.value())
                .query((resultSet, rowNumber) -> new StoredRequest(
                        new RequestId(resultSet.getObject("request_id", UUID.class)),
                        Operation.valueOf(resultSet.getString("operation")),
                        new FacilityId(resultSet.getObject("request_facility_id", UUID.class)),
                        new VehicleIdentifier(resultSet.getString("request_vehicle_identifier")),
                        mapSession(resultSet, rowNumber)))
                .optional();
    }

    private Optional<ParkingSession> findActiveSession(
            FacilityId facilityId,
            VehicleIdentifier vehicleIdentifier,
            boolean lock) {
        String sql = lock ? ACTIVE_SESSION + " FOR UPDATE OF ps" : ACTIVE_SESSION;
        return jdbcClient.sql(sql)
                .param("facilityId", facilityId.value())
                .param("vehicleIdentifier", vehicleIdentifier.value())
                .query(this::mapSession)
                .optional();
    }

    private ParkingSession replayEnter(
            StoredRequest stored,
            FacilityId facilityId,
            VehicleIdentifier vehicleIdentifier,
            SizeClass requiredSize) {
        if (stored.operation() != Operation.ENTER
                || !stored.facilityId().equals(facilityId)
                || !stored.vehicleIdentifier().equals(vehicleIdentifier)
                || stored.session().requiredSize() != requiredSize) {
            throw new IdempotencyConflictException(stored.requestId());
        }
        return stored.session();
    }

    private ParkingSessionExit replayExit(
            StoredRequest stored,
            FacilityId facilityId,
            VehicleIdentifier vehicleIdentifier) {
        if (stored.operation() != Operation.EXIT
                || !stored.facilityId().equals(facilityId)
                || !stored.vehicleIdentifier().equals(vehicleIdentifier)) {
            throw new IdempotencyConflictException(stored.requestId());
        }
        ParkingReceipt receipt = pricingService.findReceipt(stored.session().id().value())
                .orElseThrow(() -> new IllegalStateException("completed session has no receipt"));
        return new ParkingSessionExit(stored.session(), receipt);
    }

    private void insertSession(ParkingSession session) {
        SpaceLocation location = session.spaceLocation();
        jdbcClient.sql("""
                INSERT INTO parking_sessions (
                    id,
                    facility_id,
                    vehicle_identifier,
                    required_size,
                    floor_number,
                    zone_code,
                    space_number,
                    status,
                    entered_at,
                    reservation_id
                )
                VALUES (
                    :id,
                    :facilityId,
                    :vehicleIdentifier,
                    :requiredSize,
                    :floorNumber,
                    :zoneCode,
                    :spaceNumber,
                    :status,
                    :enteredAt,
                    :reservationId
                )
                """)
                .param("id", session.id().value())
                .param("facilityId", session.facilityId().value())
                .param("vehicleIdentifier", session.vehicleIdentifier().value())
                .param("requiredSize", session.requiredSize().name())
                .param("floorNumber", location.floorNumber().value())
                .param("zoneCode", location.zoneCode().value())
                .param("spaceNumber", location.spaceNumber().value())
                .param("status", session.status().name())
                .param("enteredAt", databaseTime(session.enteredAt()))
                .param(
                        "reservationId",
                        Optional.ofNullable(session.reservationId())
                                .map(ReservationId::value)
                                .orElse(null))
                .update();
    }

    private void insertRequest(
            RequestId requestId,
            Operation operation,
            ParkingSession session) {
        jdbcClient.sql("""
                INSERT INTO parking_session_requests (
                    request_id,
                    operation,
                    facility_id,
                    vehicle_identifier,
                    session_id
                )
                VALUES (
                    :requestId,
                    :operation,
                    :facilityId,
                    :vehicleIdentifier,
                    :sessionId
                )
                """)
                .param("requestId", requestId.value())
                .param("operation", operation.name())
                .param("facilityId", session.facilityId().value())
                .param("vehicleIdentifier", session.vehicleIdentifier().value())
                .param("sessionId", session.id().value())
                .update();
    }

    private ParkingSession mapSession(ResultSet resultSet, int rowNumber) throws SQLException {
        FacilityId facilityId = new FacilityId(resultSet.getObject("facility_id", UUID.class));
        OffsetDateTime exitedAt = resultSet.getObject("exited_at", OffsetDateTime.class);
        UUID reservationId = resultSet.getObject("reservation_id", UUID.class);
        return new ParkingSession(
                new SessionId(resultSet.getObject("id", UUID.class)),
                facilityId,
                new VehicleIdentifier(resultSet.getString("vehicle_identifier")),
                SizeClass.valueOf(resultSet.getString("required_size")),
                new SpaceLocation(
                        facilityId,
                        new FloorNumber(resultSet.getInt("floor_number")),
                        new ZoneCode(resultSet.getString("zone_code")),
                        new SpaceNumber(resultSet.getInt("space_number"))),
                resultSet.getObject("entered_at", OffsetDateTime.class).toInstant(),
                exitedAt == null ? null : exitedAt.toInstant(),
                reservationId == null ? null : new ReservationId(reservationId));
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private static OffsetDateTime databaseTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static void requireCommandArguments(
            RequestId requestId,
            FacilityId facilityId,
            VehicleIdentifier vehicleIdentifier) {
        Objects.requireNonNull(requestId, "requestId must not be null");
        requireLookupArguments(facilityId, vehicleIdentifier);
    }

    private static void requireLookupArguments(
            FacilityId facilityId,
            VehicleIdentifier vehicleIdentifier) {
        Objects.requireNonNull(facilityId, "facilityId must not be null");
        Objects.requireNonNull(vehicleIdentifier, "vehicleIdentifier must not be null");
    }

    private enum Operation {
        ENTER,
        EXIT
    }

    private record StoredRequest(
            RequestId requestId,
            Operation operation,
            FacilityId facilityId,
            VehicleIdentifier vehicleIdentifier,
            ParkingSession session) {
    }
}
