package com.jenish.smartparking.allocation.domain;

public final class VehicleAlreadyParkedException extends IllegalStateException {

    public VehicleAlreadyParkedException(VehicleIdentifier vehicleIdentifier) {
        super("vehicle is already parked: " + vehicleIdentifier.value());
    }
}
