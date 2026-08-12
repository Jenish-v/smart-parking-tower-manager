package com.jenish.smartparking.facility.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ParkingZone(ZoneCode code, List<ParkingSpace> spaces) {

    public ParkingZone {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(spaces, "spaces must not be null");
        if (spaces.isEmpty()) {
            throw new IllegalArgumentException("zone must contain at least one space");
        }

        Set<SpaceNumber> numbers = new HashSet<>();
        for (ParkingSpace space : spaces) {
            Objects.requireNonNull(space, "spaces must not contain null");
            if (!numbers.add(space.number())) {
                throw new IllegalArgumentException("space numbers must be unique within a zone");
            }
        }
        spaces = spaces.stream().sorted((left, right) -> left.number().compareTo(right.number())).toList();
    }

    public int capacity() {
        return spaces.size();
    }

    public long operationalCapacity() {
        return spaces.stream()
                .filter(space -> space.operationalState() == SpaceOperationalState.ACTIVE)
                .count();
    }
}
