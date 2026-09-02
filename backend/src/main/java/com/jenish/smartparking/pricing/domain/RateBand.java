package com.jenish.smartparking.pricing.domain;

import java.util.Objects;

public record RateBand(Money incrementCharge, Money rollingDayCap) {

    public RateBand {
        Objects.requireNonNull(incrementCharge, "incrementCharge must not be null");
        Objects.requireNonNull(rollingDayCap, "rollingDayCap must not be null");
        if (incrementCharge.minorUnits() == 0) {
            throw new IllegalArgumentException("incrementCharge must be positive");
        }
        if (!incrementCharge.currency().equals(rollingDayCap.currency())) {
            throw new IllegalArgumentException("rate-band currencies must match");
        }
        if (rollingDayCap.minorUnits() < incrementCharge.minorUnits()) {
            throw new IllegalArgumentException("rollingDayCap must cover at least one increment");
        }
    }
}
