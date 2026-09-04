package com.jenish.smartparking.parkingsession.application;

import com.jenish.smartparking.allocation.domain.VehicleIdentifier;
import com.jenish.smartparking.facility.domain.FacilityId;
import com.jenish.smartparking.facility.domain.SizeClass;
import com.jenish.smartparking.parkingsession.domain.ParkingSession;
import com.jenish.smartparking.parkingsession.domain.RequestId;
import java.util.List;
import java.util.Optional;

public interface ParkingSessionService {

    ParkingSession enter(
            RequestId requestId,
            FacilityId facilityId,
            VehicleIdentifier vehicleIdentifier,
            SizeClass requiredSize);

    ParkingSessionExit exit(
            RequestId requestId,
            FacilityId facilityId,
            VehicleIdentifier vehicleIdentifier);

    Optional<ParkingSession> findActive(
            FacilityId facilityId,
            VehicleIdentifier vehicleIdentifier);

    List<ParkingSession> history(
            FacilityId facilityId,
            VehicleIdentifier vehicleIdentifier);
}
