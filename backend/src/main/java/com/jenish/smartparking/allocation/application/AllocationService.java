package com.jenish.smartparking.allocation.application;

import com.jenish.smartparking.allocation.domain.ParkingAllocation;
import com.jenish.smartparking.allocation.domain.VehicleIdentifier;
import com.jenish.smartparking.facility.domain.FacilityId;
import com.jenish.smartparking.facility.domain.SizeClass;
import java.util.Optional;

public interface AllocationService {

    ParkingAllocation park(
            FacilityId facilityId,
            VehicleIdentifier vehicleIdentifier,
            SizeClass requiredSize);

    Optional<ParkingAllocation> find(
            FacilityId facilityId,
            VehicleIdentifier vehicleIdentifier);

    ParkingAllocation unpark(
            FacilityId facilityId,
            VehicleIdentifier vehicleIdentifier);
}
