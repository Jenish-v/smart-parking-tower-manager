package com.jenish.smartparking.pricing.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ParkingReceipt(
        UUID id,
        UUID sessionId,
        FeeQuote quote,
        Instant issuedAt) {

    public ParkingReceipt {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(quote, "quote must not be null");
        Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        if (issuedAt.isBefore(quote.exitedAt())) {
            throw new IllegalArgumentException("issuedAt must not precede exit time");
        }
    }
}
