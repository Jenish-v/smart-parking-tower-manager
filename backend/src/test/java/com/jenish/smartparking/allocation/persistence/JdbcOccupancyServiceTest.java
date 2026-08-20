package com.jenish.smartparking.allocation.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jenish.smartparking.allocation.application.AllocationService;
import com.jenish.smartparking.allocation.application.OccupancyService;
import com.jenish.smartparking.allocation.application.OccupancySnapshot;
import com.jenish.smartparking.allocation.domain.VehicleIdentifier;
import com.jenish.smartparking.facility.domain.FacilityId;
import com.jenish.smartparking.facility.domain.FacilityNotFoundException;
import com.jenish.smartparking.facility.domain.SizeClass;
import java.util.UUID;
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
class JdbcOccupancyServiceTest {

    private static final FacilityId FACILITY_ID =
            new FacilityId(UUID.fromString("d936bb7d-3027-47aa-a47b-d04a37e07310"));

    @Container
    @ServiceConnection
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17.5-alpine");

    @Autowired
    private OccupancyService occupancyService;

    @Autowired
    private AllocationService allocationService;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void resetOccupancy() {
        jdbcClient.sql("DELETE FROM active_allocations").update();
        jdbcClient.sql("UPDATE parking_spaces SET operational_state = 'ACTIVE'").update();
    }

    @Test
    void reportsTheReferenceCapacityByFloor() {
        OccupancySnapshot snapshot = occupancyService.getOccupancy(FACILITY_ID);

        assertEquals(7_200, snapshot.totalSpaces());
        assertEquals(7_200, snapshot.operationalSpaces());
        assertEquals(0, snapshot.occupiedSpaces());
        assertEquals(7_200, snapshot.availableSpaces());
        assertEquals(6, snapshot.floors().size());
        snapshot.floors().forEach(floor -> {
            assertEquals(1_200, floor.totalSpaces());
            assertEquals(1_200, floor.operationalSpaces());
            assertEquals(1_200, floor.availableSpaces());
        });
    }

    @Test
    void excludesOutOfServiceAndOccupiedSpacesFromAvailability() {
        allocationService.park(FACILITY_ID, new VehicleIdentifier("SMALL ONE"), SizeClass.SMALL);
        allocationService.park(FACILITY_ID, new VehicleIdentifier("MEDIUM ONE"), SizeClass.MEDIUM);
        allocationService.park(FACILITY_ID, new VehicleIdentifier("LARGE ONE"), SizeClass.LARGE);
        markLastSpaceOutOfService();

        OccupancySnapshot snapshot = occupancyService.getOccupancy(FACILITY_ID);

        assertEquals(7_200, snapshot.totalSpaces());
        assertEquals(7_199, snapshot.operationalSpaces());
        assertEquals(3, snapshot.occupiedSpaces());
        assertEquals(7_196, snapshot.availableSpaces());
        assertEquals(3, snapshot.floors().getFirst().occupiedSpaces());
        assertEquals(1_199, snapshot.floors().getLast().operationalSpaces());
    }

    @Test
    void rejectsAnUnknownFacility() {
        FacilityId unknown = new FacilityId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

        assertThrows(FacilityNotFoundException.class, () -> occupancyService.getOccupancy(unknown));
    }

    private void markLastSpaceOutOfService() {
        int changed = jdbcClient.sql("""
                UPDATE parking_spaces ps
                SET operational_state = 'OUT_OF_SERVICE'
                FROM parking_zones pz, parking_floors pf
                WHERE ps.zone_id = pz.id
                  AND pz.floor_id = pf.id
                  AND pf.facility_id = :facilityId
                  AND pf.floor_number = 6
                  AND pz.code = 'F'
                  AND ps.space_number = 200
                """)
                .param("facilityId", FACILITY_ID.value())
                .update();
        assertEquals(1, changed);
    }
}
