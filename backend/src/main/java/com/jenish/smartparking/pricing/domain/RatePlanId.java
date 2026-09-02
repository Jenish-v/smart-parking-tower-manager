package com.jenish.smartparking.pricing.domain;

import java.util.Objects;
import java.util.UUID;

public record RatePlanId(UUID value) {

    public RatePlanId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static RatePlanId newId() {
        return new RatePlanId(UUID.randomUUID());
    }
}
