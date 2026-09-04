package com.jenish.smartparking.pricing.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdjustFeeRequest(
        @NotNull
        @Min(-1_000_000_000L)
        @Max(1_000_000_000L)
        Long amountMinor,
        @NotBlank
        @Pattern(regexp = "CUSTOMER_SERVICE|RATE_CORRECTION|OPERATIONAL_EXCEPTION|OTHER")
        String reason,
        @NotBlank
        @Size(max = 240)
        String reasonDetail,
        @NotBlank
        @Size(max = 64)
        String operatorReference) {
}
