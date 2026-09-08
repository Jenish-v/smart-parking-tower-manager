package com.jenish.smartparking.identity;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jenish.smartparking.audit.application.AuditHistory;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "smart-parking.security.enabled=true",
        "smart-parking.security.issuer-uri=https://identity.example.test",
        "spring.flyway.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration"
})
@AutoConfigureMockMvc
class ApiAuthorizationTest {

    private static final String FACILITY_ID = "d936bb7d-3027-47aa-a47b-d04a37e07310";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private AuditHistory auditHistory;

    @Test
    void leavesHealthAndContractEndpointsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/openapi.yaml"))
                .andExpect(status().isOk());
    }

    @Test
    void reservesPrometheusMetricsForAdministrators() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERATOR"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(get("/actuator/prometheus")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN));
    }

    @Test
    void returnsAStableProblemWhenAuthenticationIsMissing() throws Exception {
        mockMvc.perform(get("/api/v1/unmapped"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void permitsOperatorsToUseParkingCommands() throws Exception {
        mockMvc.perform(post("/api/v1/facilities/" + FACILITY_ID + "/parking-sessions/entries")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERATOR")))
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reservesFeeAdjustmentsForAdministrators() throws Exception {
        String path = "/api/v1/facilities/" + FACILITY_ID
                + "/parking-sessions/" + UUID.randomUUID()
                + "/receipt/adjustments/" + UUID.randomUUID();
        mockMvc.perform(put(path)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERATOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(put(path)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mapsOidcRolesToSpringAuthorities() {
        Jwt token = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("operator-1")
                .claim("roles", List.of("OPERATOR", "ADMIN"))
                .build();

        var authentication = SecurityConfiguration.jwtAuthenticationConverter().convert(token);

        org.junit.jupiter.api.Assertions.assertTrue(authentication.getAuthorities().contains(
                new SimpleGrantedAuthority("ROLE_OPERATOR")));
        org.junit.jupiter.api.Assertions.assertTrue(authentication.getAuthorities().contains(
                new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Test
    void reservesAuditHistoryForAdministrators() throws Exception {
        when(auditHistory.find(any(), any(), eq(100))).thenReturn(List.of());
        String path = "/api/v1/facilities/" + FACILITY_ID + "/audit-events";

        mockMvc.perform(get(path)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERATOR"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(get(path)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }
}
