package com.jenish.smartparking.facility.domain;

public record SpaceNumber(int value) implements Comparable<SpaceNumber> {

    public SpaceNumber {
        if (value < 1) {
            throw new IllegalArgumentException("space number must be positive");
        }
    }

    @Override
    public int compareTo(SpaceNumber other) {
        return Integer.compare(value, other.value);
    }
}
