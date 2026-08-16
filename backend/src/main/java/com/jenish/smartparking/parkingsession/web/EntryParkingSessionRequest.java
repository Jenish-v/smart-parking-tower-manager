package com.jenish.smartparking.parkingsession.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EntryParkingSessionRequest(
        @NotBlank
        @Size(max = 32)
        String vehicleIdentifier,
        @NotBlank
        @Pattern(regexp = "SMALL|MEDIUM|LARGE")
        String requiredSize) {
}
