package com.jenish.smartparking.parkingsession.application;

import com.jenish.smartparking.parkingsession.domain.RequestId;

public final class IdempotencyConflictException extends IllegalStateException {

    public IdempotencyConflictException(RequestId requestId) {
        super("request identifier was already used for a different command: " + requestId.value());
    }
}
