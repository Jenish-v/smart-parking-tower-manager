package com.jenish.smartparking.facility.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ParkingFloor(FloorNumber number, List<ParkingZone> zones) {

    public ParkingFloor {
        Objects.requireNonNull(number, "number must not be null");
        Objects.requireNonNull(zones, "zones must not be null");
        if (zones.isEmpty()) {
            throw new IllegalArgumentException("floor must contain at least one zone");
        }

        Set<ZoneCode> codes = new HashSet<>();
        for (ParkingZone zone : zones) {
            Objects.requireNonNull(zone, "zones must not contain null");
            if (!codes.add(zone.code())) {
                throw new IllegalArgumentException("zone codes must be unique within a floor");
            }
        }
        zones = zones.stream().sorted((left, right) -> left.code().compareTo(right.code())).toList();
    }

    public int capacity() {
        return zones.stream().mapToInt(ParkingZone::capacity).sum();
    }
}
