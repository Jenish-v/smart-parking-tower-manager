package com.jenish.smartparking.facility.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record Facility(FacilityId id, String name, List<ParkingFloor> floors) {

    private static final int MAXIMUM_NAME_LENGTH = 120;

    public Facility {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(floors, "floors must not be null");
        name = name.strip();
        if (name.isEmpty() || name.length() > MAXIMUM_NAME_LENGTH) {
            throw new IllegalArgumentException("facility name must contain between 1 and 120 characters");
        }
        if (floors.isEmpty()) {
            throw new IllegalArgumentException("facility must contain at least one floor");
        }

        Set<FloorNumber> numbers = new HashSet<>();
        for (ParkingFloor floor : floors) {
            Objects.requireNonNull(floor, "floors must not contain null");
            if (!numbers.add(floor.number())) {
                throw new IllegalArgumentException("floor numbers must be unique within a facility");
            }
        }
        floors = floors.stream().sorted((left, right) -> left.number().compareTo(right.number())).toList();
    }

    public int capacity() {
        return floors.stream().mapToInt(ParkingFloor::capacity).sum();
    }
}
