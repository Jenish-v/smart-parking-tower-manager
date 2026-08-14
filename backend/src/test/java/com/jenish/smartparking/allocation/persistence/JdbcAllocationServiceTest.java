package com.jenish.smartparking.allocation.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jenish.smartparking.allocation.application.AllocationService;
import com.jenish.smartparking.allocation.domain.ParkingAllocation;
import com.jenish.smartparking.allocation.domain.ParkingCapacityExceededException;
import com.jenish.smartparking.allocation.domain.VehicleAlreadyParkedException;
import com.jenish.smartparking.allocation.domain.VehicleIdentifier;
import com.jenish.smartparking.facility.domain.FacilityId;
import com.jenish.smartparking.facility.domain.SizeClass;
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
class JdbcAllocationServiceTest {

    private static final FacilityId FACILITY_ID =
            new FacilityId(UUID.fromString("d936bb7d-3027-47aa-a47b-d04a37e07310"));

    @Container
    @ServiceConnection
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17.5-alpine");

    @Autowired
    private AllocationService allocationService;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void resetAllocationsAndCapacity() {
        jdbcClient.sql("DELETE FROM active_allocations").update();
        jdbcClient.sql("UPDATE parking_spaces SET operational_state = 'OUT_OF_SERVICE'").update();
    }

    @Test
    void persistsDeterministicAllocationAndRelease() {
        activateSpace(2, "A", 1);
        activateSpace(1, "B", 1);
        activateSpace(1, "A", 101);

        VehicleIdentifier vehicle = new VehicleIdentifier("tor 101");
        ParkingAllocation allocation = allocationService.park(FACILITY_ID, vehicle, SizeClass.SMALL);

        assertEquals(1, allocation.spaceLocation().floorNumber().value());
        assertEquals("A", allocation.spaceLocation().zoneCode().value());
        assertEquals(101, allocation.spaceLocation().spaceNumber().value());
        assertEquals(allocation, allocationService.find(FACILITY_ID, vehicle).orElseThrow());
        assertEquals(allocation, allocationService.unpark(FACILITY_ID, vehicle));
        assertTrue(allocationService.find(FACILITY_ID, vehicle).isEmpty());
        assertEquals(1L, countReleasedAllocations());
    }

    @Test
    void preventsConcurrentAssignmentsForOneVehicle() throws Exception {
        activateSpace(1, "A", 1);
        activateSpace(1, "A", 2);
        VehicleIdentifier vehicle = new VehicleIdentifier("same vehicle");

        List<Attempt> results = runConcurrently(
                () -> attemptPark(vehicle),
                () -> attemptPark(vehicle));

        assertEquals(1L, results.stream().filter(Attempt::success).count());
        assertEquals(1L, results.stream().filter(Attempt::duplicateVehicle).count());
        assertEquals(1L, countActiveAllocations());
    }

    @Test
    void preventsConcurrentAssignmentsForOneSpace() throws Exception {
        activateSpace(1, "A", 1);

        List<Attempt> results = runConcurrently(
                () -> attemptPark(new VehicleIdentifier("vehicle one")),
                () -> attemptPark(new VehicleIdentifier("vehicle two")));

        assertEquals(1L, results.stream().filter(Attempt::success).count());
        assertEquals(1L, results.stream().filter(Attempt::capacityExceeded).count());
        assertEquals(1L, countActiveAllocations());
    }

    private Attempt attemptPark(VehicleIdentifier vehicleIdentifier) {
        try {
            allocationService.park(FACILITY_ID, vehicleIdentifier, SizeClass.SMALL);
            return Attempt.SUCCESS;
        } catch (VehicleAlreadyParkedException exception) {
            return Attempt.DUPLICATE_VEHICLE;
        } catch (ParkingCapacityExceededException exception) {
            return Attempt.CAPACITY_EXCEEDED;
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
                throw new IllegalStateException("concurrent allocation did not start");
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

    private enum Attempt {
        SUCCESS,
        DUPLICATE_VEHICLE,
        CAPACITY_EXCEEDED;

        private boolean success() {
            return this == SUCCESS;
        }

        private boolean duplicateVehicle() {
            return this == DUPLICATE_VEHICLE;
        }

        private boolean capacityExceeded() {
            return this == CAPACITY_EXCEEDED;
        }
    }
}
