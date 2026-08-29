package com.jenish.smartparking.allocation.domain;

import com.jenish.smartparking.facility.domain.SizeClass;

public final class ParkingCapacityExceededException extends IllegalStateException {

    public ParkingCapacityExceededException(SizeClass requiredSize) {
        super("no compatible parking space is available for size: " + requiredSize);
    }
}
