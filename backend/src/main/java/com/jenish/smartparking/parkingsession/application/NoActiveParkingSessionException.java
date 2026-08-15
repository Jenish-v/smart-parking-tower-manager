package com.jenish.smartparking.parkingsession.application;

import com.jenish.smartparking.allocation.domain.VehicleIdentifier;

public final class NoActiveParkingSessionException extends IllegalStateException {

    public NoActiveParkingSessionException(VehicleIdentifier vehicleIdentifier) {
        super("vehicle has no active parking session: " + vehicleIdentifier.value());
    }
}
