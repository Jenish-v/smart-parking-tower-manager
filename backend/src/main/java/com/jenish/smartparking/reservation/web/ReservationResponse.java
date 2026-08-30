package com.jenish.smartparking.reservation.web;

import com.jenish.smartparking.reservation.domain.Reservation;
import java.time.Instant;
import java.util.UUID;

public record ReservationResponse(
        UUID reservationId,
        UUID facilityId,
        String vehicleIdentifier,
        String requiredSize,
        Instant startsAt,
        Instant endsAt,
        Instant createdAt,
        String status,
        Instant resolvedAt) {

    static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.id().value(),
                reservation.facilityId().value(),
                reservation.vehicleIdentifier().value(),
                reservation.requiredSize().name(),
                reservation.window().startsAt(),
                reservation.window().endsAt(),
                reservation.createdAt(),
                reservation.status().name(),
                reservation.resolvedAt());
    }
}

