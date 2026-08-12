package com.jenish.smartparking.facility.domain;

public record FloorNumber(int value) implements Comparable<FloorNumber> {

    public FloorNumber {
        if (value < 1) {
            throw new IllegalArgumentException("floor number must be positive");
        }
    }

    @Override
    public int compareTo(FloorNumber other) {
        return Integer.compare(value, other.value);
    }
}
