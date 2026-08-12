package com.jenish.smartparking.facility.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/devdata")
@Testcontainers(disabledWithoutDocker = true)
class FacilityMigrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:17.5-alpine");

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void migratesTheReferenceFacility() {
        assertEquals(1L, count("facilities"));
        assertEquals(6L, count("parking_floors"));
        assertEquals(36L, count("parking_zones"));
        assertEquals(7_200L, count("parking_spaces"));
        assertEquals(3_600L, countSpacesBySize("SMALL"));
        assertEquals(2_880L, countSpacesBySize("MEDIUM"));
        assertEquals(720L, countSpacesBySize("LARGE"));
    }

    @Test
    void enforcesFacilityHierarchyUniqueness() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbcClient.sql("""
                INSERT INTO parking_floors (id, facility_id, floor_number)
                VALUES (:id, :facilityId, 1)
                """)
                .param("id", UUID.randomUUID())
                .param("facilityId", UUID.fromString("d936bb7d-3027-47aa-a47b-d04a37e07310"))
                .update());
    }

    private long count(String table) {
        return jdbcClient.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    private long countSpacesBySize(String sizeClass) {
        return jdbcClient.sql("SELECT count(*) FROM parking_spaces WHERE size_class = :sizeClass")
                .param("sizeClass", sizeClass)
                .query(Long.class)
                .single();
    }
}
