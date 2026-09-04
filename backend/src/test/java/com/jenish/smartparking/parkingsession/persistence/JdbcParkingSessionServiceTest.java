package com.jenish.smartparking.parkingsession.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jenish.smartparking.allocation.domain.ParkingCapacityExceededException;
import com.jenish.smartparking.allocation.domain.VehicleIdentifier;
import com.jenish.smartparking.facility.domain.FacilityId;
import com.jenish.smartparking.facility.domain.SizeClass;
import com.jenish.smartparking.parkingsession.application.ActiveParkingSessionExistsException;
import com.jenish.smartparking.parkingsession.application.IdempotencyConflictException;
import com.jenish.smartparking.parkingsession.application.NoActiveParkingSessionException;
import com.jenish.smartparking.parkingsession.application.ParkingSessionService;
import com.jenish.smartparking.parkingsession.application.ParkingSessionExit;
import com.jenish.smartparking.parkingsession.domain.ParkingSession;
import com.jenish.smartparking.parkingsession.domain.ParkingSessionStatus;
import com.jenish.smartparking.parkingsession.domain.RequestId;
import com.jenish.smartparking.pricing.application.NoApplicableRatePlanException;
import com.jenish.smartparking.reservation.application.ReservationArrivalSizeMismatchException;
import com.jenish.smartparking.reservation.domain.ReservationId;
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
class JdbcParkingSessionServiceTest {

    private static final FacilityId FACILITY_ID =
            new FacilityId(UUID.fromString("d936bb7d-3027-47aa-a47b-d04a37e07310"));

    @Container
    @ServiceConnection
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17.5-alpine");

    @Autowired
    private ParkingSessionService sessionService;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void resetSessionsAndCapacity() {
        jdbcClient.sql("DELETE FROM parking_receipts").update();
        jdbcClient.sql("DELETE FROM parking_session_requests").update();
        jdbcClient.sql("DELETE FROM parking_sessions").update();
        jdbcClient.sql("DELETE FROM reservations").update();
        jdbcClient.sql("DELETE FROM active_allocations").update();
        jdbcClient.sql("UPDATE parking_spaces SET operational_state = 'OUT_OF_SERVICE'").update();
    }

    @Test
    void recordsIdempotentEntryExitAndHistory() {
        activateSpace(1, "A", 1);
        VehicleIdentifier vehicle = new VehicleIdentifier("tor 501");
        RequestId entryRequest = RequestId.newId();

        ParkingSession entered = sessionService.enter(
                entryRequest,
                FACILITY_ID,
                vehicle,
                SizeClass.SMALL);
        ParkingSession entryReplay = sessionService.enter(
                entryRequest,
                FACILITY_ID,
                vehicle,
                SizeClass.SMALL);

        assertEquals(entered, entryReplay);
        assertEquals(ParkingSessionStatus.ACTIVE, entered.status());
        assertEquals(entered, sessionService.findActive(FACILITY_ID, vehicle).orElseThrow());

        RequestId exitRequest = RequestId.newId();
        ParkingSessionExit exited = sessionService.exit(exitRequest, FACILITY_ID, vehicle);
        ParkingSessionExit exitReplay = sessionService.exit(exitRequest, FACILITY_ID, vehicle);

        assertEquals(exited, exitReplay);
        assertEquals(ParkingSessionStatus.COMPLETED, exited.session().status());
        assertEquals(entered.id().value(), exited.receipt().sessionId());
        assertEquals("CAD", exited.receipt().quote().total().currency().getCurrencyCode());
        assertTrue(sessionService.findActive(FACILITY_ID, vehicle).isEmpty());
        assertEquals(List.of(exited.session()), sessionService.history(FACILITY_ID, vehicle));
        assertEquals(2L, count("parking_session_requests"));
        assertEquals(1L, count("parking_sessions"));
        assertEquals(1L, count("parking_receipts"));
        assertEquals(1L, countReleasedAllocations());
    }

    @Test
    void serializesConcurrentRetriesForOneRequest() throws Exception {
        activateSpace(1, "A", 1);
        VehicleIdentifier vehicle = new VehicleIdentifier("retry vehicle");
        RequestId requestId = RequestId.newId();

        List<ParkingSession> results = runConcurrently(
                () -> sessionService.enter(requestId, FACILITY_ID, vehicle, SizeClass.SMALL),
                () -> sessionService.enter(requestId, FACILITY_ID, vehicle, SizeClass.SMALL));

        assertEquals(results.get(0), results.get(1));
        assertEquals(1L, count("parking_sessions"));
        assertEquals(1L, count("parking_session_requests"));
        assertEquals(1L, countActiveAllocations());
    }

    @Test
    void rejectsInvalidTransitionsAndRequestReuse() {
        activateSpace(1, "A", 1);
        VehicleIdentifier vehicle = new VehicleIdentifier("state vehicle");
        RequestId entryRequest = RequestId.newId();

        sessionService.enter(entryRequest, FACILITY_ID, vehicle, SizeClass.SMALL);

        assertThrows(
                ActiveParkingSessionExistsException.class,
                () -> sessionService.enter(
                        RequestId.newId(),
                        FACILITY_ID,
                        vehicle,
                        SizeClass.SMALL));
        assertThrows(
                IdempotencyConflictException.class,
                () -> sessionService.exit(entryRequest, FACILITY_ID, vehicle));

        sessionService.exit(RequestId.newId(), FACILITY_ID, vehicle);
        assertThrows(
                NoActiveParkingSessionException.class,
                () -> sessionService.exit(RequestId.newId(), FACILITY_ID, vehicle));
    }

    @Test
    void rollsBackARejectedEntry() {
        VehicleIdentifier vehicle = new VehicleIdentifier("no capacity");

        assertThrows(
                ParkingCapacityExceededException.class,
                () -> sessionService.enter(
                        RequestId.newId(),
                        FACILITY_ID,
                        vehicle,
                        SizeClass.SMALL));

        assertEquals(0L, count("parking_sessions"));
        assertEquals(0L, count("parking_session_requests"));
        assertEquals(0L, countActiveAllocations());
    }

    @Test
    void fulfillsMatchingReservationAndLinksItToTheSession() {
        activateSpace(1, "A", 1);
        VehicleIdentifier vehicle = new VehicleIdentifier("reserved vehicle");
        ReservationId reservationId = insertConfirmedReservation(vehicle, SizeClass.SMALL);

        ParkingSession entered = sessionService.enter(
                RequestId.newId(),
                FACILITY_ID,
                vehicle,
                SizeClass.SMALL);

        assertEquals(reservationId, entered.reservationId());
        assertEquals("FULFILLED", reservationStatus(reservationId));
        assertEquals(entered.enteredAt(), reservationResolvedAt(reservationId));
    }

    @Test
    void rollsBackFulfillmentWhenSpaceAllocationFails() {
        VehicleIdentifier vehicle = new VehicleIdentifier("reserved no space");
        ReservationId reservationId = insertConfirmedReservation(vehicle, SizeClass.SMALL);

        assertThrows(
                ParkingCapacityExceededException.class,
                () -> sessionService.enter(
                        RequestId.newId(),
                        FACILITY_ID,
                        vehicle,
                        SizeClass.SMALL));

        assertEquals("CONFIRMED", reservationStatus(reservationId));
        assertEquals(0L, count("parking_sessions"));
    }

    @Test
    void rejectsAnArrivalThatDoesNotMatchTheReservedSize() {
        activateSpace(1, "A", 1);
        VehicleIdentifier vehicle = new VehicleIdentifier("wrong size");
        ReservationId reservationId = insertConfirmedReservation(vehicle, SizeClass.MEDIUM);

        assertThrows(
                ReservationArrivalSizeMismatchException.class,
                () -> sessionService.enter(
                        RequestId.newId(),
                        FACILITY_ID,
                        vehicle,
                        SizeClass.SMALL));

        assertEquals("CONFIRMED", reservationStatus(reservationId));
        assertEquals(0L, count("parking_sessions"));
        assertEquals(0L, countActiveAllocations());
    }

    @Test
    void keepsTheSessionAndAllocationActiveWhenPricingIsUnavailable() {
        activateSpace(1, "A", 1);
        VehicleIdentifier vehicle = new VehicleIdentifier("missing price");
        sessionService.enter(RequestId.newId(), FACILITY_ID, vehicle, SizeClass.SMALL);
        deleteReferenceRatePlan();

        try {
            assertThrows(
                    NoApplicableRatePlanException.class,
                    () -> sessionService.exit(RequestId.newId(), FACILITY_ID, vehicle));

            assertTrue(sessionService.findActive(FACILITY_ID, vehicle).isPresent());
            assertEquals(1L, countActiveAllocations());
            assertEquals(0L, count("parking_receipts"));
        } finally {
            insertReferenceRatePlan();
        }
    }

    private List<ParkingSession> runConcurrently(
            Callable<ParkingSession> first,
            Callable<ParkingSession> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<ParkingSession> firstResult = executor.submit(gated(first, ready, start));
            Future<ParkingSession> secondResult = executor.submit(gated(second, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            return List.of(
                    firstResult.get(10, TimeUnit.SECONDS),
                    secondResult.get(10, TimeUnit.SECONDS));
        }
    }

    private Callable<ParkingSession> gated(
            Callable<ParkingSession> operation,
            CountDownLatch ready,
            CountDownLatch start) {
        return () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent session request did not start");
            }
            return operation.call();
        };
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

    private ReservationId insertConfirmedReservation(
            VehicleIdentifier vehicleIdentifier,
            SizeClass requiredSize) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        ReservationId reservationId = ReservationId.newId();
        jdbcClient.sql("""
                INSERT INTO reservations (
                    id, facility_id, vehicle_identifier, required_size,
                    starts_at, ends_at, created_at, status
                ) VALUES (
                    :id, :facilityId, :vehicleIdentifier, :requiredSize,
                    :startsAt, :endsAt, :createdAt, 'CONFIRMED'
                )
                """)
                .param("id", reservationId.value())
                .param("facilityId", FACILITY_ID.value())
                .param("vehicleIdentifier", vehicleIdentifier.value())
                .param("requiredSize", requiredSize.name())
                .param("startsAt", databaseTime(now.minus(5, ChronoUnit.MINUTES)))
                .param("endsAt", databaseTime(now.plus(1, ChronoUnit.HOURS)))
                .param("createdAt", databaseTime(now.minus(10, ChronoUnit.MINUTES)))
                .update();
        return reservationId;
    }

    private String reservationStatus(ReservationId reservationId) {
        return jdbcClient.sql("SELECT status FROM reservations WHERE id = :id")
                .param("id", reservationId.value())
                .query(String.class)
                .single();
    }

    private void deleteReferenceRatePlan() {
        jdbcClient.sql("DELETE FROM pricing_rate_bands").update();
        jdbcClient.sql("DELETE FROM pricing_rate_plans").update();
    }

    private void insertReferenceRatePlan() {
        jdbcClient.sql("""
                INSERT INTO pricing_rate_plans (
                    id, version, name, effective_from, effective_until,
                    grace_seconds, billing_increment_seconds, currency
                ) VALUES (
                    'acd13eb1-c151-4c4c-a83b-dd16c11bd0ef', 1, 'Reference CAD rate',
                    '2000-01-01T00:00:00Z', NULL, 600, 900, 'CAD'
                )
                """).update();
        jdbcClient.sql("""
                INSERT INTO pricing_rate_bands (
                    rate_plan_id, rate_plan_version, size_class,
                    increment_charge_minor, rolling_day_cap_minor
                ) VALUES
                    ('acd13eb1-c151-4c4c-a83b-dd16c11bd0ef', 1, 'SMALL', 125, 2000),
                    ('acd13eb1-c151-4c4c-a83b-dd16c11bd0ef', 1, 'MEDIUM', 150, 2500),
                    ('acd13eb1-c151-4c4c-a83b-dd16c11bd0ef', 1, 'LARGE', 200, 3000)
                """).update();
    }

    private Instant reservationResolvedAt(ReservationId reservationId) {
        return jdbcClient.sql("SELECT resolved_at FROM reservations WHERE id = :id")
                .param("id", reservationId.value())
                .query(OffsetDateTime.class)
                .single()
                .toInstant();
    }

    private OffsetDateTime databaseTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private long count(String table) {
        return jdbcClient.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    private long countActiveAllocations() {
        return jdbcClient.sql("""
                SELECT count(*)
                FROM active_allocations
                WHERE released_at IS NULL
                """)
                .query(Long.class)
                .single();
    }

    private long countReleasedAllocations() {
        return jdbcClient.sql("""
                SELECT count(*)
                FROM active_allocations
                WHERE released_at IS NOT NULL
                """)
                .query(Long.class)
                .single();
    }
}
