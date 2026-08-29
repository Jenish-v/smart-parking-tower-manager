package com.jenish.smartparking.reservation.domain;

public final class InvalidReservationStateException extends IllegalStateException {

    public InvalidReservationStateException(ReservationId reservationId, String reason) {
        super("Reservation " + reservationId.value() + " " + reason);
    }
}
