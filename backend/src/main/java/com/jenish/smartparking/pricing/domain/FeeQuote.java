package com.jenish.smartparking.pricing.domain;

import com.jenish.smartparking.facility.domain.SizeClass;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record FeeQuote(
        RatePlanId ratePlanId,
        long ratePlanVersion,
        SizeClass sizeClass,
        Instant enteredAt,
        Instant exitedAt,
        Duration billableDuration,
        long billingIncrements,
        Money grossCharge,
        Money capDiscount,
        Money total) {

    public FeeQuote {
        Objects.requireNonNull(ratePlanId, "ratePlanId must not be null");
        Objects.requireNonNull(sizeClass, "sizeClass must not be null");
        Objects.requireNonNull(enteredAt, "enteredAt must not be null");
        Objects.requireNonNull(exitedAt, "exitedAt must not be null");
        Objects.requireNonNull(billableDuration, "billableDuration must not be null");
        Objects.requireNonNull(grossCharge, "grossCharge must not be null");
        Objects.requireNonNull(capDiscount, "capDiscount must not be null");
        Objects.requireNonNull(total, "total must not be null");
        if (ratePlanVersion <= 0 || billableDuration.isNegative() || billingIncrements < 0) {
            throw new IllegalArgumentException("quote values must not be negative");
        }
        if (!grossCharge.subtract(capDiscount).equals(total)) {
            throw new IllegalArgumentException("total must equal gross charge less cap discount");
        }
    }
}
