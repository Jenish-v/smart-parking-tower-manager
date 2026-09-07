package com.jenish.smartparking.audit.web;

import com.jenish.smartparking.audit.domain.AuditEvent;
import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
        UUID eventId,
        UUID facilityId,
        String actorSubject,
        String action,
        String targetType,
        UUID targetId,
        Instant occurredAt) {

    static AuditEventResponse from(AuditEvent event) {
        return new AuditEventResponse(
                event.id(),
                event.facilityId().value(),
                event.actorSubject(),
                event.action().name(),
                event.targetType().name(),
                event.targetId(),
                event.occurredAt());
    }
}
