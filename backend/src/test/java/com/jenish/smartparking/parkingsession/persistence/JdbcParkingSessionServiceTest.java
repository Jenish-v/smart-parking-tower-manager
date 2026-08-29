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
import com.jenish.smartparking.parkingsession.domain.ParkingSession;
import com.jenish.smartparking.parkingsession.domain.ParkingSessionStatus;
import com.jenish.smartparking.parkingsession.domain.RequestId;
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
        jdbcClient.sql("DELETE FROM parking_session_requests").update();
        jdbcClient.sql("DELETE FROM parking_sessions").update();
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
        ParkingSession exited = sessionService.exit(exitRequest, FACILITY_ID, vehicle);
        ParkingSession exitReplay = sessionService.exit(exitRequest, FACILITY_ID, vehicle);

        assertEquals(exited, exitReplay);
        assertEquals(ParkingSessionStatus.COMPLETED, exited.status());
        assertTrue(sessionService.findActive(FACILITY_ID, vehicle).isEmpty());
        assertEquals(List.of(exited), sessionService.history(FACILITY_ID, vehicle));
        assertEquals(2L, count("parking_session_requests"));
        assertEquals(1L, count("parking_sessions"));
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
