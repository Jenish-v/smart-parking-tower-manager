package com.jenish.smartparking.audit.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jenish.smartparking.audit.application.AuditEventRecorder;
import com.jenish.smartparking.audit.application.AuditHistory;
import com.jenish.smartparking.audit.domain.AuditAction;
import com.jenish.smartparking.audit.domain.AuditEvent;
import com.jenish.smartparking.audit.domain.AuditTargetType;
import com.jenish.smartparking.facility.domain.FacilityId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/devdata")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class JdbcAuditServiceTest {

    private static final FacilityId FACILITY_ID = new FacilityId(
            UUID.fromString("d936bb7d-3027-47aa-a47b-d04a37e07310"));

    @Container
    @ServiceConnection
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17.5-alpine");

    @Autowired
    private AuditEventRecorder recorder;

    @Autowired
    private AuditHistory history;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void appendsAndReadsFacilityHistoryNewestFirst() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        AuditEvent older = event(now.minusSeconds(1));
        AuditEvent newer = event(now);

        recorder.record(older);
        recorder.record(newer);

        assertEquals(List.of(newer, older), history.find(FACILITY_ID, now.plusSeconds(1), 100));
        assertEquals(List.of(older), history.find(FACILITY_ID, now, 1));
    }

    @Test
    void rejectsUpdatesToPersistedAuditFacts() {
        AuditEvent event = event(Instant.now());
        recorder.record(event);

        assertThrows(DataAccessException.class, () -> jdbcClient.sql("""
                UPDATE audit_events SET actor_subject = 'replacement' WHERE id = :id
                """).param("id", event.id()).update());
    }

    @Test
    void rejectsDeletionOfPersistedAuditFacts() {
        AuditEvent event = event(Instant.now());
        recorder.record(event);

        assertThrows(DataAccessException.class, () -> jdbcClient.sql("""
                DELETE FROM audit_events WHERE id = :id
                """).param("id", event.id()).update());
    }

    private AuditEvent event(Instant occurredAt) {
        return new AuditEvent(
                UUID.randomUUID(),
                FACILITY_ID,
                "identity-provider|operator-17",
                AuditAction.FEE_ADJUSTMENT_APPENDED,
                AuditTargetType.RECEIPT,
                UUID.randomUUID(),
                occurredAt);
    }
}
