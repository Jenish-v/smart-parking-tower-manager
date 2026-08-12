package com.jenish.smartparking.facility.domain;

import java.util.Objects;

public record ParkingSpace(
        SpaceNumber number,
        SizeClass sizeClass,
        SpaceOperationalState operationalState) {

    public ParkingSpace {
        Objects.requireNonNull(number, "number must not be null");
        Objects.requireNonNull(sizeClass, "sizeClass must not be null");
        Objects.requireNonNull(operationalState, "operationalState must not be null");
    }

    public boolean canAccept(SizeClass requiredSize) {
        Objects.requireNonNull(requiredSize, "requiredSize must not be null");
        return operationalState == SpaceOperationalState.ACTIVE
                && sizeClass.canAccommodate(requiredSize);
    }

    public ParkingSpace takeOutOfService() {
        return withOperationalState(SpaceOperationalState.OUT_OF_SERVICE);
    }

    public ParkingSpace returnToService() {
        return withOperationalState(SpaceOperationalState.ACTIVE);
    }

    private ParkingSpace withOperationalState(SpaceOperationalState state) {
        if (operationalState == state) {
            return this;
        }
        return new ParkingSpace(number, sizeClass, state);
    }
}
