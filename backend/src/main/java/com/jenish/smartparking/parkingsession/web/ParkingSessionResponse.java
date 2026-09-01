package com.jenish.smartparking.parkingsession.web;

import com.jenish.smartparking.parkingsession.domain.ParkingSession;
import java.time.Instant;
import java.util.UUID;

public record ParkingSessionResponse(
        UUID sessionId,
        UUID facilityId,
        String vehicleIdentifier,
        String requiredSize,
        String status,
        SpaceResponse space,
        Instant enteredAt,
        Instant exitedAt,
        UUID reservationId) {

    public static ParkingSessionResponse from(ParkingSession session) {
        return new ParkingSessionResponse(
                session.id().value(),
                session.facilityId().value(),
                session.vehicleIdentifier().value(),
                session.requiredSize().name(),
                session.status().name(),
                new SpaceResponse(
                        session.spaceLocation().floorNumber().value(),
                        session.spaceLocation().zoneCode().value(),
                        session.spaceLocation().spaceNumber().value()),
                session.enteredAt(),
                session.exitedAt(),
                session.reservationId() == null ? null : session.reservationId().value());
    }

    public record SpaceResponse(
            int floorNumber,
            String zoneCode,
            int spaceNumber) {
    }
}
