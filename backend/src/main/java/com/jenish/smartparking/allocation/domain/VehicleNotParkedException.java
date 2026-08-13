package com.jenish.smartparking.allocation.domain;

public final class VehicleNotParkedException extends IllegalStateException {

    public VehicleNotParkedException(VehicleIdentifier vehicleIdentifier) {
        super("vehicle is not parked: " + vehicleIdentifier.value());
    }
}
