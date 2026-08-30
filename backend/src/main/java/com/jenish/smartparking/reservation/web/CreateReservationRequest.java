package com.jenish.smartparking.reservation.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateReservationRequest(
        @NotBlank @Size(max = 32) String vehicleIdentifier,
        @NotBlank String requiredSize,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt) {
}

