package com.jenish.smartparking.identity;

import java.util.Objects;

public record ActorIdentity(String subject) {

    public ActorIdentity {
        Objects.requireNonNull(subject, "subject must not be null");
        subject = subject.trim();
        if (subject.isEmpty() || subject.length() > 255) {
            throw new IllegalArgumentException("subject must contain between 1 and 255 characters");
        }
    }
}
