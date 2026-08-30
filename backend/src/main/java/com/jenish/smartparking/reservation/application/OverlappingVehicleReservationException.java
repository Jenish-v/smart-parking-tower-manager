package com.jenish.smartparking.reservation.application;

import com.jenish.smartparking.allocation.domain.VehicleIdentifier;

public final class OverlappingVehicleReservationException extends RuntimeException {

    public OverlappingVehicleReservationException(VehicleIdentifier vehicleIdentifier) {
        super("Vehicle " + vehicleIdentifier.value() + " already has an overlapping reservation");
    }
}

