package com.jenish.smartparking.reporting.web;

import com.jenish.smartparking.allocation.application.OccupancySnapshot;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OccupancyResponse(
        UUID facilityId,
        Instant capturedAt,
        long totalSpaces,
        long operationalSpaces,
        long occupiedSpaces,
        long availableSpaces,
        List<FloorOccupancyResponse> floors) {

    public static OccupancyResponse from(OccupancySnapshot snapshot) {
        return new OccupancyResponse(
                snapshot.facilityId().value(),
                snapshot.capturedAt(),
                snapshot.totalSpaces(),
                snapshot.operationalSpaces(),
                snapshot.occupiedSpaces(),
                snapshot.availableSpaces(),
                snapshot.floors().stream()
                        .map(floor -> new FloorOccupancyResponse(
                                floor.floorNumber(),
                                floor.totalSpaces(),
                                floor.operationalSpaces(),
                                floor.occupiedSpaces(),
                                floor.availableSpaces()))
                        .toList());
    }

    public record FloorOccupancyResponse(
            int floorNumber,
            long totalSpaces,
            long operationalSpaces,
            long occupiedSpaces,
            long availableSpaces) {
    }
}
