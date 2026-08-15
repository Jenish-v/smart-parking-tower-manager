package com.jenish.smartparking.parkingsession.domain;

import java.util.Objects;
import java.util.UUID;

public record RequestId(UUID value) {

    public RequestId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static RequestId newId() {
        return new RequestId(UUID.randomUUID());
    }
}
