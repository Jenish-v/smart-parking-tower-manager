package com.jenish.smartparking.parkingsession.domain;

import com.jenish.smartparking.allocation.domain.SpaceLocation;
import com.jenish.smartparking.allocation.domain.VehicleIdentifier;
import com.jenish.smartparking.facility.domain.FacilityId;
import com.jenish.smartparking.facility.domain.SizeClass;
import java.time.Instant;
import java.util.Objects;

public record ParkingSession(
        SessionId id,
        FacilityId facilityId,
        VehicleIdentifier vehicleIdentifier,
        SizeClass requiredSize,
        SpaceLocation spaceLocation,
        Instant enteredAt,
        Instant exitedAt) {

    public ParkingSession {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(facilityId, "facilityId must not be null");
        Objects.requireNonNull(vehicleIdentifier, "vehicleIdentifier must not be null");
        Objects.requireNonNull(requiredSize, "requiredSize must not be null");
        Objects.requireNonNull(spaceLocation, "spaceLocation must not be null");
        Objects.requireNonNull(enteredAt, "enteredAt must not be null");
        if (!spaceLocation.facilityId().equals(facilityId)) {
            throw new IllegalArgumentException("space location must belong to the session facility");
        }
        if (exitedAt != null && exitedAt.isBefore(enteredAt)) {
            throw new IllegalArgumentException("exit time must not be before entry time");
        }
    }

    public static ParkingSession start(
            SessionId id,
            FacilityId facilityId,
            VehicleIdentifier vehicleIdentifier,
            SizeClass requiredSize,
            SpaceLocation spaceLocation,
            Instant enteredAt) {
        return new ParkingSession(
                id,
                facilityId,
                vehicleIdentifier,
                requiredSize,
                spaceLocation,
                enteredAt,
                null);
    }

    public ParkingSession complete(Instant completedAt) {
        Objects.requireNonNull(completedAt, "completedAt must not be null");
        if (status() == ParkingSessionStatus.COMPLETED) {
            throw new InvalidSessionStateException(id, "is already completed");
        }
        if (completedAt.isBefore(enteredAt)) {
            throw new InvalidSessionStateException(id, "cannot complete before entry");
        }
        return new ParkingSession(
                id,
                facilityId,
                vehicleIdentifier,
                requiredSize,
                spaceLocation,
                enteredAt,
                completedAt);
    }

    public ParkingSessionStatus status() {
        return exitedAt == null ? ParkingSessionStatus.ACTIVE : ParkingSessionStatus.COMPLETED;
    }
}
