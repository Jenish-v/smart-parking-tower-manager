package com.jenish.smartparking.allocation.domain;

import com.jenish.smartparking.facility.domain.FacilityId;
import com.jenish.smartparking.facility.domain.FloorNumber;
import com.jenish.smartparking.facility.domain.SpaceNumber;
import com.jenish.smartparking.facility.domain.ZoneCode;
import java.util.Comparator;
import java.util.Objects;

public record SpaceLocation(
        FacilityId facilityId,
        FloorNumber floorNumber,
        ZoneCode zoneCode,
        SpaceNumber spaceNumber) implements Comparable<SpaceLocation> {

    private static final Comparator<SpaceLocation> ORDER = Comparator
            .comparing((SpaceLocation location) -> location.facilityId().value())
            .thenComparing(SpaceLocation::floorNumber)
            .thenComparing(SpaceLocation::zoneCode)
            .thenComparing(SpaceLocation::spaceNumber);

    public SpaceLocation {
        Objects.requireNonNull(facilityId, "facilityId must not be null");
        Objects.requireNonNull(floorNumber, "floorNumber must not be null");
        Objects.requireNonNull(zoneCode, "zoneCode must not be null");
        Objects.requireNonNull(spaceNumber, "spaceNumber must not be null");
    }

    @Override
    public int compareTo(SpaceLocation other) {
        return ORDER.compare(this, other);
    }
}
