package com.jenish.smartparking.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public final class ActorIdentityResolver {

    private static final ActorIdentity LOCAL_ACTOR = new ActorIdentity("local-development");

    private final boolean securityEnabled;

    public ActorIdentityResolver(
            @Value("${smart-parking.security.enabled:true}") boolean securityEnabled) {
        this.securityEnabled = securityEnabled;
    }

    public ActorIdentity resolve(Authentication authentication) {
        if (!securityEnabled) {
            return LOCAL_ACTOR;
        }
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new IllegalArgumentException("A JWT authentication is required for an audited action");
        }
        return new ActorIdentity(token.getToken().getSubject());
    }
}
