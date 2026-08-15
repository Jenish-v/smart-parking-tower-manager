package com.jenish.smartparking.parkingsession.domain;

public final class InvalidSessionStateException extends IllegalStateException {

    public InvalidSessionStateException(SessionId sessionId, String reason) {
        super("parking session " + sessionId.value() + " " + reason);
    }
}
