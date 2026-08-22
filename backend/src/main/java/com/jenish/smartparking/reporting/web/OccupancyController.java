package com.jenish.smartparking.reporting.web;

import com.jenish.smartparking.allocation.application.OccupancyService;
import com.jenish.smartparking.facility.domain.FacilityId;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/facilities/{facilityId}/occupancy")
public final class OccupancyController {

    private final OccupancyService occupancyService;
    private final OccupancyStreamService occupancyStreamService;

    public OccupancyController(
            @Lazy OccupancyService occupancyService,
            OccupancyStreamService occupancyStreamService) {
        this.occupancyService = occupancyService;
        this.occupancyStreamService = occupancyStreamService;
    }

    @GetMapping
    public OccupancyResponse getOccupancy(@PathVariable UUID facilityId) {
        return OccupancyResponse.from(occupancyService.getOccupancy(new FacilityId(facilityId)));
    }

    @GetMapping(path = "/stream", produces = "text/event-stream")
    public SseEmitter streamOccupancy(@PathVariable UUID facilityId) {
        FacilityId id = new FacilityId(facilityId);
        return occupancyStreamService.subscribe(id, occupancyService.getOccupancy(id));
    }
}
