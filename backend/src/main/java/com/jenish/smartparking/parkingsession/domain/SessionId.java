package com.jenish.smartparking.parkingsession.domain;

import java.util.Objects;
import java.util.UUID;

public record SessionId(UUID value) {

    public SessionId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static SessionId newId() {
        return new SessionId(UUID.randomUUID());
    }
}
