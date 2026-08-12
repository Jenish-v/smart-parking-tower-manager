package com.jenish.smartparking.facility.domain;

import java.util.Locale;
import java.util.Objects;

public record ZoneCode(String value) implements Comparable<ZoneCode> {

    public ZoneCode {
        Objects.requireNonNull(value, "value must not be null");
        value = value.strip().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z][A-Z0-9]{0,7}")) {
            throw new IllegalArgumentException("zone code must contain one to eight uppercase letters or digits");
        }
    }

    @Override
    public int compareTo(ZoneCode other) {
        return value.compareTo(other.value);
    }
}
