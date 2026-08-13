package com.jenish.smartparking.allocation.domain;

import com.jenish.smartparking.facility.domain.SizeClass;
import java.util.Map;
import java.util.Objects;

public record AvailabilitySnapshot(
        long operationalSpaces,
        long occupiedSpaces,
        Map<SizeClass, Long> availableByPhysicalSize,
        Map<SizeClass, Long> compatibleAvailability) {

    public AvailabilitySnapshot {
        if (operationalSpaces < 0 || occupiedSpaces < 0 || occupiedSpaces > operationalSpaces) {
            throw new IllegalArgumentException("availability counts are inconsistent");
        }
        Objects.requireNonNull(availableByPhysicalSize, "availableByPhysicalSize must not be null");
        Objects.requireNonNull(compatibleAvailability, "compatibleAvailability must not be null");
        availableByPhysicalSize = Map.copyOf(availableByPhysicalSize);
        compatibleAvailability = Map.copyOf(compatibleAvailability);
    }

    public long availableSpaces() {
        return operationalSpaces - occupiedSpaces;
    }

    public long availableFor(SizeClass requiredSize) {
        Objects.requireNonNull(requiredSize, "requiredSize must not be null");
        return compatibleAvailability.getOrDefault(requiredSize, 0L);
    }
}
