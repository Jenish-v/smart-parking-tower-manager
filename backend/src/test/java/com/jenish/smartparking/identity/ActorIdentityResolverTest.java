package com.jenish.smartparking.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class ActorIdentityResolverTest {

    private final ActorIdentityResolver resolver = new ActorIdentityResolver(true);

    @Test
    void derivesTheActorFromTheSignedTokenSubject() {
        Jwt token = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("identity-provider|operator-17")
                .build();

        ActorIdentity actor = resolver.resolve(new JwtAuthenticationToken(token));

        assertEquals("identity-provider|operator-17", actor.subject());
    }

    @Test
    void usesAnExplicitActorOnlyWhenAuthenticationIsDisabled() {
        var unverified = UsernamePasswordAuthenticationToken.authenticated(
                "caller-supplied-name",
                "ignored",
                List.of());

        assertEquals("local-development", new ActorIdentityResolver(false).resolve(unverified).subject());
    }

    @Test
    void rejectsAnUnverifiedAuthenticationImplementation() {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "caller-supplied-name",
                "ignored",
                List.of());

        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(authentication));
    }
}
