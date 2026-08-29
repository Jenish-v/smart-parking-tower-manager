package com.jenish.smartparking.facility.domain;

public final class FacilityNotFoundException extends RuntimeException {

    public FacilityNotFoundException(FacilityId facilityId) {
        super("Facility " + facilityId.value() + " was not found");
    }
}
