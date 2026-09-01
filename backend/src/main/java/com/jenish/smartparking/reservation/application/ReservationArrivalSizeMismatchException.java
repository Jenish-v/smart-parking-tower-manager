package com.jenish.smartparking.reservation.application;

import com.jenish.smartparking.facility.domain.SizeClass;
import com.jenish.smartparking.reservation.domain.ReservationId;

public final class ReservationArrivalSizeMismatchException extends RuntimeException {

    public ReservationArrivalSizeMismatchException(
            ReservationId reservationId,
            SizeClass reservedSize,
            SizeClass arrivalSize) {
        super("Reservation " + reservationId.value() + " requires " + reservedSize
                + " but the arrival requested " + arrivalSize);
    }
}
