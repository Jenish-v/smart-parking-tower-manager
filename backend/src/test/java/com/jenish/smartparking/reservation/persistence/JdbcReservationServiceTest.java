package com.jenish.smartparking.reservation.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jenish.smartparking.allocation.domain.VehicleIdentifier;
import com.jenish.smartparking.facility.domain.FacilityId;
import com.jenish.smartparking.facility.domain.SizeClass;
import com.jenish.smartparking.reservation.application.OverlappingVehicleReservationException;
import com.jenish.smartparking.reservation.application.ReservationCapacityExceededException;
import com.jenish.smartparking.reservation.application.ReservationIdentifierConflictException;
import com.jenish.smartparking.reservation.application.ReservationService;
import com.jenish.smartparking.reservation.domain.Reservation;
import com.jenish.smartparking.reservation.domain.ReservationId;
import com.jenish.smartparking.reservation.domain.ReservationStatus;
import com.jenish.smartparking.reservation.domain.ReservationWindow;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/devdata")
@Testcontainers(disabledWithoutDocker = true)
class JdbcReservationServiceTest {

    private static final FacilityId FACILITY_ID =
            new FacilityId(UUID.fromString("d936bb7d-3027-47aa-a47b-d04a37e07310"));

    @Container
    @ServiceConnection
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17.5-alpine");

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void resetReservationsAndCapacity() {
        jdbcClient.sql("DELETE FROM reservations").update();
        jdbcClient.sql("UPDATE parking_spaces SET operational_state = 'OUT_OF_SERVICE'").update();
    }

    @Test
    void persistsCancellationAndVehicleHistory() {
        activateSpace(1, "A", 1);
        ReservationWindow window = futureWindow(1, 2);
        VehicleIdentifier vehicle = new VehicleIdentifier("tor 700");

        Reservation confirmed = reservationService.create(
                ReservationId.newId(),
                FACILITY_ID,
                vehicle,
                SizeClass.SMALL,
                window);
        Reservation cancelled = reservationService.cancel(FACILITY_ID, confirmed.id());

        assertEquals(ReservationStatus.CANCELLED, cancelled.status());
        assertEquals(cancelled, reservationService.find(FACILITY_ID, confirmed.id()).orElseThrow());
        assertEquals(List.of(cancelled), reservationService.history(FACILITY_ID, vehicle));
    }

    @Test
    void replaysCreateAndCancellationByReservationIdentifier() {
        activateSpace(1, "A", 1);
        ReservationId reservationId = ReservationId.newId();
        ReservationWindow window = futureWindow(1, 2);
        VehicleIdentifier vehicle = new VehicleIdentifier("retry car");

        Reservation created = reservationService.create(
                reservationId,
                FACILITY_ID,
                vehicle,
                SizeClass.SMALL,
                window);
        Reservation replayed = reservationService.create(
                reservationId,
                FACILITY_ID,
                vehicle,
                SizeClass.SMALL,
                window);

        assertEquals(created, replayed);
        assertThrows(
                ReservationIdentifierConflictException.class,
                () -> reservationService.create(
                        reservationId,
                        FACILITY_ID,
                        new VehicleIdentifier("other car"),
                        SizeClass.SMALL,
                        window));

        Reservation cancelled = reservationService.cancel(FACILITY_ID, reservationId);
        assertEquals(cancelled, reservationService.cancel(FACILITY_ID, reservationId));
    }

    @Test
    void protectsNestedCompatibleCapacityPools() {
        activateSpace(1, "A", 181);
        ReservationWindow window = futureWindow(1, 2);

        reservationService.create(
                ReservationId.newId(),
                FACILITY_ID,
                new VehicleIdentifier("large one"),
                SizeClass.LARGE,
                window);

        assertThrows(
                ReservationCapacityExceededException.class,
                () -> reservationService.create(
                        ReservationId.newId(),
                        FACILITY_ID,
                        new VehicleIdentifier("medium two"),
                        SizeClass.MEDIUM,
                        window));
    }

    @Test
    void acceptsAdjacentHalfOpenWindows() {
        activateSpace(1, "A", 1);
        Instant startsAt = futureTime(1);
        Instant boundary = futureTime(2);

        reservationService.create(
                ReservationId.newId(),
                FACILITY_ID,
                new VehicleIdentifier("first car"),
                SizeClass.SMALL,
                new ReservationWindow(startsAt, boundary));
        Reservation second = reservationService.create(
                ReservationId.newId(),
                FACILITY_ID,
                new VehicleIdentifier("second car"),
                SizeClass.SMALL,
                new ReservationWindow(boundary, futureTime(3)));

        assertEquals(ReservationStatus.CONFIRMED, second.status());
    }

    @Test
    void rejectsOverlappingWindowsForOneVehicle() {
        activateSpace(1, "A", 1);
        activateSpace(1, "A", 2);
        VehicleIdentifier vehicle = new VehicleIdentifier("same car");

        reservationService.create(
                ReservationId.newId(),
                FACILITY_ID,
                vehicle,
                SizeClass.SMALL,
                futureWindow(1, 3));

        assertThrows(
                OverlappingVehicleReservationException.class,
                () -> reservationService.create(
                        ReservationId.newId(),
                        FACILITY_ID,
                        vehicle,
                        SizeClass.SMALL,
                        futureWindow(2, 4)));
    }

    @Test
    void serializesConcurrentCapacityClaims() throws Exception {
        activateSpace(1, "A", 1);
        ReservationWindow window = futureWindow(1, 2);

        List<Attempt> attempts = runConcurrently(
                () -> attemptCreate("concurrent one", window),
                () -> attemptCreate("concurrent two", window));

        assertEquals(1L, attempts.stream().filter(Attempt::success).count());
        Throwable failure = attempts.stream()
                .filter(attempt -> !attempt.success())
                .findFirst()
                .orElseThrow()
                .failure();
        assertInstanceOf(ReservationCapacityExceededException.class, failure);
        assertEquals(1L, countConfirmed());
    }

    @Test
    void expiresDueReservationsDuringReads() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        ReservationId reservationId = ReservationId.newId();
        insertConfirmed(
                reservationId,
                now.minus(3, ChronoUnit.HOURS),
                now.minus(2, ChronoUnit.HOURS),
                now.minus(4, ChronoUnit.HOURS));

        Reservation expired = reservationService.find(FACILITY_ID, reservationId).orElseThrow();

        assertEquals(ReservationStatus.EXPIRED, expired.status());
        assertFalse(expired.resolvedAt().isBefore(expired.window().endsAt()));
    }

    private Attempt attemptCreate(String vehicleIdentifier, ReservationWindow window) {
        try {
            reservationService.create(
                    ReservationId.newId(),
                    FACILITY_ID,
                    new VehicleIdentifier(vehicleIdentifier),
                    SizeClass.SMALL,
                    window);
            return new Attempt(true, null);
        } catch (RuntimeException exception) {
            return new Attempt(false, exception);
        }
    }

    private List<Attempt> runConcurrently(
            Callable<Attempt> first,
            Callable<Attempt> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Attempt> firstResult = executor.submit(gated(first, ready, start));
            Future<Attempt> secondResult = executor.submit(gated(second, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            return List.of(
                    firstResult.get(10, TimeUnit.SECONDS),
                    secondResult.get(10, TimeUnit.SECONDS));
        }
    }

    private Callable<Attempt> gated(
            Callable<Attempt> operation,
            CountDownLatch ready,
            CountDownLatch start) {
        return () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent reservation request did not start");
            }
            return operation.call();
        };
    }

    private ReservationWindow futureWindow(long startHours, long endHours) {
        return new ReservationWindow(futureTime(startHours), futureTime(endHours));
    }

    private Instant futureTime(long hours) {
        return Instant.now().plus(hours, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
    }

    private void insertConfirmed(
            ReservationId reservationId,
            Instant startsAt,
            Instant endsAt,
            Instant createdAt) {
        jdbcClient.sql("""
                INSERT INTO reservations (
                    id, facility_id, vehicle_identifier, required_size,
                    starts_at, ends_at, created_at, status
                ) VALUES (
                    :id, :facilityId, 'EXPIRED TEST', 'SMALL',
                    :startsAt, :endsAt, :createdAt, 'CONFIRMED'
                )
                """)
                .param("id", reservationId.value())
                .param("facilityId", FACILITY_ID.value())
                .param("startsAt", OffsetDateTime.ofInstant(startsAt, ZoneOffset.UTC))
                .param("endsAt", OffsetDateTime.ofInstant(endsAt, ZoneOffset.UTC))
                .param("createdAt", OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC))
                .update();
    }

    private void activateSpace(int floorNumber, String zoneCode, int spaceNumber) {
        int changed = jdbcClient.sql("""
                UPDATE parking_spaces ps
                SET operational_state = 'ACTIVE'
                FROM parking_zones pz, parking_floors pf
                WHERE ps.zone_id = pz.id
                  AND pz.floor_id = pf.id
                  AND pf.facility_id = :facilityId
                  AND pf.floor_number = :floorNumber
                  AND pz.code = :zoneCode
                  AND ps.space_number = :spaceNumber
                """)
                .param("facilityId", FACILITY_ID.value())
                .param("floorNumber", floorNumber)
                .param("zoneCode", zoneCode)
                .param("spaceNumber", spaceNumber)
                .update();
        assertEquals(1, changed);
    }

    private long countConfirmed() {
        return jdbcClient.sql("SELECT count(*) FROM reservations WHERE status = 'CONFIRMED'")
                .query(Long.class)
                .single();
    }

    private record Attempt(boolean success, Throwable failure) {
    }
}
