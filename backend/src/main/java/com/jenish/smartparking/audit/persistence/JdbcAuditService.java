package com.jenish.smartparking.audit.persistence;

import com.jenish.smartparking.audit.application.AuditEventRecorder;
import com.jenish.smartparking.audit.application.AuditHistory;
import com.jenish.smartparking.audit.domain.AuditAction;
import com.jenish.smartparking.audit.domain.AuditEvent;
import com.jenish.smartparking.audit.domain.AuditTargetType;
import com.jenish.smartparking.facility.domain.FacilityId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
@Lazy
public final class JdbcAuditService implements AuditEventRecorder, AuditHistory {

    private final JdbcClient jdbcClient;

    public JdbcAuditService(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient must not be null");
    }

    @Override
    public void record(AuditEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        jdbcClient.sql("""
                INSERT INTO audit_events (
                    id, facility_id, actor_subject, action,
                    target_type, target_id, occurred_at
                ) VALUES (
                    :id, :facilityId, :actorSubject, :action,
                    :targetType, :targetId, :occurredAt
                )
                """)
                .param("id", event.id())
                .param("facilityId", event.facilityId().value())
                .param("actorSubject", event.actorSubject())
                .param("action", event.action().name())
                .param("targetType", event.targetType().name())
                .param("targetId", event.targetId())
                .param("occurredAt", databaseTime(event.occurredAt()))
                .update();
    }

    @Override
    public List<AuditEvent> find(FacilityId facilityId, Instant beforeExclusive, int limit) {
        Objects.requireNonNull(facilityId, "facilityId must not be null");
        Objects.requireNonNull(beforeExclusive, "beforeExclusive must not be null");
        if (limit < 1 || limit > 500) {
            throw new IllegalArgumentException("limit must be between 1 and 500");
        }
        return jdbcClient.sql("""
                SELECT id, facility_id, actor_subject, action,
                       target_type, target_id, occurred_at
                FROM audit_events
                WHERE facility_id = :facilityId
                  AND occurred_at < :beforeExclusive
                ORDER BY occurred_at DESC, id DESC
                LIMIT :limit
                """)
                .param("facilityId", facilityId.value())
                .param("beforeExclusive", databaseTime(beforeExclusive))
                .param("limit", limit)
                .query(this::mapEvent)
                .list();
    }

    private AuditEvent mapEvent(ResultSet resultSet, int rowNumber) throws SQLException {
        return new AuditEvent(
                resultSet.getObject("id", java.util.UUID.class),
                new FacilityId(resultSet.getObject("facility_id", java.util.UUID.class)),
                resultSet.getString("actor_subject"),
                AuditAction.valueOf(resultSet.getString("action")),
                AuditTargetType.valueOf(resultSet.getString("target_type")),
                resultSet.getObject("target_id", java.util.UUID.class),
                resultSet.getObject("occurred_at", OffsetDateTime.class).toInstant());
    }

    private static OffsetDateTime databaseTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
