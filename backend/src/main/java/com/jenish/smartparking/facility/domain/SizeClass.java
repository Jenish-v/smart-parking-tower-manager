package com.jenish.smartparking.facility.domain;

import java.util.Objects;

public enum SizeClass {
    SMALL(1),
    MEDIUM(2),
    LARGE(3);

    private final int rank;

    SizeClass(int rank) {
        this.rank = rank;
    }

    public boolean canAccommodate(SizeClass requiredSize) {
        Objects.requireNonNull(requiredSize, "requiredSize must not be null");
        return rank >= requiredSize.rank;
    }
}
