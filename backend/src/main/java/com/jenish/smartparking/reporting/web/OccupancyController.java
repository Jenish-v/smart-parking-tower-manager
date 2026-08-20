package com.jenish.smartparking.reporting.web;

import com.jenish.smartparking.allocation.application.OccupancyService;
import com.jenish.smartparking.facility.domain.FacilityId;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/facilities/{facilityId}/occupancy")
public final class OccupancyController {

    private final OccupancyService occupancyService;

    public OccupancyController(@Lazy OccupancyService occupancyService) {
        this.occupancyService = occupancyService;
    }

    @GetMapping
    public OccupancyResponse getOccupancy(@PathVariable UUID facilityId) {
        return OccupancyResponse.from(occupancyService.getOccupancy(new FacilityId(facilityId)));
    }
}
