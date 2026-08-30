package com.jenish.smartparking.reservation.application;

import com.jenish.smartparking.reservation.domain.ReservationId;

public final class ReservationIdentifierConflictException extends RuntimeException {

    public ReservationIdentifierConflictException(ReservationId reservationId) {
        super("Reservation identifier " + reservationId.value() + " is already used by another request");
    }
}
