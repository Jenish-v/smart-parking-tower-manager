package com.jenish.smartparking.reservation.domain;

import java.time.Instant;
import java.util.Objects;

public record ReservationWindow(Instant startsAt, Instant endsAt) {

    public ReservationWindow {
        Objects.requireNonNull(startsAt, "startsAt must not be null");
        Objects.requireNonNull(endsAt, "endsAt must not be null");
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("reservation end must be after its start");
        }
    }

    public boolean contains(Instant instant) {
        Objects.requireNonNull(instant, "instant must not be null");
        return !instant.isBefore(startsAt) && instant.isBefore(endsAt);
    }
}
