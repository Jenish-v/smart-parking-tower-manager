package com.jenish.smartparking.pricing.application;

import java.util.UUID;

public final class AdjustmentIdentifierConflictException extends RuntimeException {

    public AdjustmentIdentifierConflictException(UUID adjustmentId) {
        super("Adjustment identifier " + adjustmentId + " is already bound to different facts");
    }
}
