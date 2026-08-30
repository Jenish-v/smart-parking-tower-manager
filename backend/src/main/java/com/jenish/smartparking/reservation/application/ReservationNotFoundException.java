package com.jenish.smartparking.reservation.application;

import com.jenish.smartparking.reservation.domain.ReservationId;

public final class ReservationNotFoundException extends RuntimeException {

    public ReservationNotFoundException(ReservationId reservationId) {
        super("Reservation " + reservationId.value() + " was not found");
    }
}

