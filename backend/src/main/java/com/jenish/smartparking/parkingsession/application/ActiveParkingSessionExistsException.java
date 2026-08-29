package com.jenish.smartparking.parkingsession.application;

import com.jenish.smartparking.allocation.domain.VehicleIdentifier;

public final class ActiveParkingSessionExistsException extends IllegalStateException {

    public ActiveParkingSessionExistsException(VehicleIdentifier vehicleIdentifier) {
        super("vehicle already has an active parking session: " + vehicleIdentifier.value());
    }
}
