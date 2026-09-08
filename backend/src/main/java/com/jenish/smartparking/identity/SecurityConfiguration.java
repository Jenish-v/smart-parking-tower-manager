package com.jenish.smartparking.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    private static final String ADJUSTMENT_PATH =
            "/api/v1/facilities/*/parking-sessions/*/receipt/adjustments/*";

    private static final String AUDIT_PATH = "/api/v1/facilities/*/audit-events";

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectMapper objectMapper,
            @Value("${smart-parking.security.enabled:true}") boolean enabled) throws Exception {
        http.csrf(csrf -> csrf.disable());
        if (!enabled) {
            return http
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                    .build();
        }

        SecurityProblemWriter problems = new SecurityProblemWriter(objectMapper);
        return http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**", "/openapi.yaml").permitAll()
                        .requestMatchers(HttpMethod.PUT, ADJUSTMENT_PATH).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, AUDIT_PATH).hasRole("ADMIN")
                        .requestMatchers("/api/v1/**").hasAnyRole("OPERATOR", "ADMIN")
                        .anyRequest().permitAll())
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(
                        jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> problems.write(
                                request,
                                response,
                                HttpStatus.UNAUTHORIZED.value(),
                                "Authentication required",
                                "A valid bearer token is required.",
                                "AUTHENTICATION_REQUIRED"))
                        .accessDeniedHandler((request, response, exception) -> problems.write(
                                request,
                                response,
                                HttpStatus.FORBIDDEN.value(),
                                "Access denied",
                                "The authenticated principal does not have the required role.",
                                "ACCESS_DENIED")))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "smart-parking.security.enabled", havingValue = "true")
    JwtDecoder jwtDecoder(@Value("${smart-parking.security.issuer-uri:}") String issuerUri) {
        if (issuerUri.isBlank()) {
            throw new IllegalStateException("OIDC issuer URI is required when security is enabled");
        }
        return JwtDecoders.fromIssuerLocation(issuerUri);
    }

    static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
