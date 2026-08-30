package com.jenish.smartparking.reservation.application;

import com.jenish.smartparking.facility.domain.SizeClass;
import com.jenish.smartparking.reservation.domain.ReservationWindow;

public final class ReservationCapacityExceededException extends RuntimeException {

    public ReservationCapacityExceededException(SizeClass requiredSize, ReservationWindow window) {
        super("No compatible reservation capacity is available for "
                + requiredSize + " during " + window.startsAt() + " to " + window.endsAt());
    }
}

