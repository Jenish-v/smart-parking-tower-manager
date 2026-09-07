package com.jenish.smartparking.audit.domain;

import com.jenish.smartparking.facility.domain.FacilityId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AuditEvent(
        UUID id,
        FacilityId facilityId,
        String actorSubject,
        AuditAction action,
        AuditTargetType targetType,
        UUID targetId,
        Instant occurredAt) {

    public AuditEvent {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(facilityId, "facilityId must not be null");
        Objects.requireNonNull(actorSubject, "actorSubject must not be null");
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(targetType, "targetType must not be null");
        Objects.requireNonNull(targetId, "targetId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        actorSubject = actorSubject.trim();
        if (actorSubject.isEmpty() || actorSubject.length() > 255) {
            throw new IllegalArgumentException("actorSubject must contain between 1 and 255 characters");
        }
    }
}
