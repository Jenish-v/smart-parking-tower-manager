package com.jenish.smartparking.allocation.application;

import com.jenish.smartparking.facility.domain.FacilityId;

public interface OccupancyService {

    OccupancySnapshot getOccupancy(FacilityId facilityId);
}
