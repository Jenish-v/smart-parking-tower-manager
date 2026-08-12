package com.jenish.smartparking.facility.domain;

import java.util.Objects;
import java.util.UUID;

public record FacilityId(UUID value) {

    public FacilityId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static FacilityId newId() {
        return new FacilityId(UUID.randomUUID());
    }
}
