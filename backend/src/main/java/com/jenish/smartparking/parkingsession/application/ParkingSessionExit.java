package com.jenish.smartparking.parkingsession.application;

import com.jenish.smartparking.parkingsession.domain.ParkingSession;
import com.jenish.smartparking.parkingsession.domain.ParkingSessionStatus;
import com.jenish.smartparking.pricing.domain.ParkingReceipt;
import java.util.Objects;

public record ParkingSessionExit(
        ParkingSession session,
        ParkingReceipt receipt) {

    public ParkingSessionExit {
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(receipt, "receipt must not be null");
        if (session.status() != ParkingSessionStatus.COMPLETED) {
            throw new IllegalArgumentException("session must be completed");
        }
        if (!session.id().value().equals(receipt.sessionId())) {
            throw new IllegalArgumentException("receipt must belong to the completed session");
        }
    }
}
