package com.jenish.smartparking.allocation.domain;

import com.jenish.smartparking.facility.domain.SizeClass;
import java.util.Objects;

public record ParkingAllocation(
        VehicleIdentifier vehicleIdentifier,
        SizeClass requiredSize,
        SpaceLocation spaceLocation) {

    public ParkingAllocation {
        Objects.requireNonNull(vehicleIdentifier, "vehicleIdentifier must not be null");
        Objects.requireNonNull(requiredSize, "requiredSize must not be null");
        Objects.requireNonNull(spaceLocation, "spaceLocation must not be null");
    }
}
