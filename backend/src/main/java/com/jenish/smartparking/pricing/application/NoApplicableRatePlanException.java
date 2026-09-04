package com.jenish.smartparking.pricing.application;

import java.time.Instant;

public final class NoApplicableRatePlanException extends RuntimeException {

    public NoApplicableRatePlanException(Instant enteredAt) {
        super("No rate plan applies at entry time " + enteredAt);
    }
}
