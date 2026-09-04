package com.jenish.smartparking.pricing.application;

import java.util.UUID;

public final class ReceiptNotFoundException extends RuntimeException {

    public ReceiptNotFoundException(UUID sessionId) {
        super("No receipt exists for parking session " + sessionId);
    }
}
