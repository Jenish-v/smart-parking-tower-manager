package com.jenish.smartparking.pricing.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record FeeAdjustment(
        UUID id,
        UUID receiptId,
        long amountMinor,
        AdjustmentReason reason,
        String reasonDetail,
        String actorSubject,
        Instant createdAt) {

    public FeeAdjustment {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(receiptId, "receiptId must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(reasonDetail, "reasonDetail must not be null");
        Objects.requireNonNull(actorSubject, "actorSubject must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        reasonDetail = reasonDetail.trim();
        actorSubject = actorSubject.trim();
        if (amountMinor == 0) {
            throw new IllegalArgumentException("amountMinor must not be zero");
        }
        if (reasonDetail.isEmpty() || reasonDetail.length() > 240) {
            throw new IllegalArgumentException("reasonDetail must contain between 1 and 240 characters");
        }
        if (actorSubject.isEmpty() || actorSubject.length() > 255) {
            throw new IllegalArgumentException("actorSubject must contain between 1 and 255 characters");
        }
    }
}
