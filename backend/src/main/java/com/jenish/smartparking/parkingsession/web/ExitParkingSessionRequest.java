package com.jenish.smartparking.parkingsession.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExitParkingSessionRequest(
        @NotBlank
        @Size(max = 32)
        String vehicleIdentifier) {
}
