package com.jenish.smartparking.allocation.domain;

import java.util.Locale;
import java.util.Objects;

public record VehicleIdentifier(String value) {

    private static final int MAXIMUM_LENGTH = 32;

    public VehicleIdentifier {
        Objects.requireNonNull(value, "value must not be null");
        value = value.strip().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        if (value.isEmpty() || value.length() > MAXIMUM_LENGTH) {
            throw new IllegalArgumentException("vehicle identifier must contain between 1 and 32 characters");
        }
        if (!value.matches("[A-Z0-9][A-Z0-9 -]*")) {
            throw new IllegalArgumentException("vehicle identifier contains unsupported characters");
        }
    }
}
