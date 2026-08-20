package com.jenish.smartparking.allocation.application;

import com.jenish.smartparking.facility.domain.FacilityId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record OccupancySnapshot(
        FacilityId facilityId,
        Instant capturedAt,
        long totalSpaces,
        long operationalSpaces,
        long occupiedSpaces,
        long availableSpaces,
        List<FloorOccupancy> floors) {

    public OccupancySnapshot {
        Objects.requireNonNull(facilityId, "facilityId must not be null");
        Objects.requireNonNull(capturedAt, "capturedAt must not be null");
        floors = List.copyOf(Objects.requireNonNull(floors, "floors must not be null"));
        requireCounts(totalSpaces, operationalSpaces, occupiedSpaces, availableSpaces);
    }

    private static void requireCounts(long total, long operational, long occupied, long available) {
        if (total < 0 || operational < 0 || occupied < 0 || available < 0
                || operational > total || occupied > total || available > operational) {
            throw new IllegalArgumentException("occupancy counts are inconsistent");
        }
    }

    public record FloorOccupancy(
            int floorNumber,
            long totalSpaces,
            long operationalSpaces,
            long occupiedSpaces,
            long availableSpaces) {

        public FloorOccupancy {
            if (floorNumber < 1) {
                throw new IllegalArgumentException("floorNumber must be positive");
            }
            requireCounts(totalSpaces, operationalSpaces, occupiedSpaces, availableSpaces);
        }
    }
}
