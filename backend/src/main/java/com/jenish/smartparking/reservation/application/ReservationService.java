package com.jenish.smartparking.reservation.application;

import com.jenish.smartparking.allocation.domain.VehicleIdentifier;
import com.jenish.smartparking.facility.domain.FacilityId;
import com.jenish.smartparking.facility.domain.SizeClass;
import com.jenish.smartparking.reservation.domain.Reservation;
import com.jenish.smartparking.reservation.domain.ReservationId;
import com.jenish.smartparking.reservation.domain.ReservationWindow;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReservationService {

    Reservation create(
            ReservationId reservationId,
            FacilityId facilityId,
            VehicleIdentifier vehicleIdentifier,
            SizeClass requiredSize,
            ReservationWindow window);

    Reservation cancel(FacilityId facilityId, ReservationId reservationId);

    Optional<Reservation> fulfillArrival(
            FacilityId facilityId,
            VehicleIdentifier vehicleIdentifier,
            SizeClass requiredSize,
            Instant arrivedAt);

    Optional<Reservation> find(FacilityId facilityId, ReservationId reservationId);

    List<Reservation> history(FacilityId facilityId, VehicleIdentifier vehicleIdentifier);
}
