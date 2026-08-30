package com.jenish.smartparking.reservation.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.flyway.locations=classpath:db/migration,classpath:db/devdata")
@Testcontainers(disabledWithoutDocker = true)
class ReservationApiTest {

    private static final UUID FACILITY_ID =
            UUID.fromString("d936bb7d-3027-47aa-a47b-d04a37e07310");

    private static final Pattern RESERVATION_ID =
            Pattern.compile("\"reservationId\":\"([^\"]+)\"");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17.5-alpine");

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void resetData() {
        jdbcClient.sql("DELETE FROM reservations").update();
        jdbcClient.sql("UPDATE parking_spaces SET operational_state = 'OUT_OF_SERVICE'").update();
        activateSpace(1, "A", 1);
    }

    @Test
    void exposesCreateLookupHistoryAndCancellation() throws Exception {
        String reservationId = UUID.randomUUID().toString();
        HttpResponse<String> created = put("/" + reservationId, createBody("TOR 801", 1, 2));

        assertEquals(201, created.statusCode());
        assertTrue(created.body().contains("\"vehicleIdentifier\":\"TOR 801\""));
        assertTrue(created.body().contains("\"status\":\"CONFIRMED\""));
        assertEquals(reservationId, reservationId(created.body()));

        HttpResponse<String> found = get("/" + reservationId);
        assertEquals(200, found.statusCode());
        assertEquals(created.body(), found.body());

        HttpResponse<String> history = get("?vehicleIdentifier=TOR%20801");
        assertEquals(200, history.statusCode());
        assertTrue(history.body().contains(reservationId));

        HttpResponse<String> cancelled = delete("/" + reservationId);
        assertEquals(200, cancelled.statusCode());
        assertTrue(cancelled.body().contains("\"status\":\"CANCELLED\""));
    }

    @Test
    void returnsStableCapacityAndValidationProblems() throws Exception {
        assertEquals(201, put("/" + UUID.randomUUID(), createBody("TOR 802", 1, 2)).statusCode());

        HttpResponse<String> capacity = put(
                "/" + UUID.randomUUID(),
                createBody("TOR 803", 1, 2));
        assertEquals(409, capacity.statusCode());
        assertProblem(capacity, "RESERVATION_CAPACITY_EXCEEDED");

        HttpResponse<String> invalid = put("/" + UUID.randomUUID(), """
                {"vehicleIdentifier":"","requiredSize":"SMALL"}
                """);
        assertEquals(400, invalid.statusCode());
        assertProblem(invalid, "VALIDATION_FAILED");
    }

    private String createBody(String vehicleIdentifier, long startHours, long endHours) {
        Instant startsAt = Instant.now().plus(startHours, ChronoUnit.HOURS)
                .truncatedTo(ChronoUnit.SECONDS);
        Instant endsAt = Instant.now().plus(endHours, ChronoUnit.HOURS)
                .truncatedTo(ChronoUnit.SECONDS);
        return """
                {"vehicleIdentifier":"%s","requiredSize":"SMALL",\
                "startsAt":"%s","endsAt":"%s"}
                """.formatted(vehicleIdentifier, startsAt, endsAt);
    }

    private HttpResponse<String> put(String suffix, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(reservationUri(suffix))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String suffix) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(reservationUri(suffix)).DELETE().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String suffix) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(reservationUri(suffix)).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI reservationUri(String suffix) {
        return URI.create("http://localhost:" + port
                + "/api/v1/facilities/" + FACILITY_ID + "/reservations" + suffix);
    }

    private String reservationId(String responseBody) {
        Matcher matcher = RESERVATION_ID.matcher(responseBody);
        assertTrue(matcher.find());
        return matcher.group(1);
    }

    private void assertProblem(HttpResponse<String> response, String code) {
        assertTrue(response.headers()
                .firstValue("Content-Type")
                .orElseThrow()
                .startsWith("application/problem+json"));
        assertTrue(response.body().contains("\"code\":\"" + code + "\""));
    }

    private void activateSpace(int floorNumber, String zoneCode, int spaceNumber) {
        int changed = jdbcClient.sql("""
                UPDATE parking_spaces ps
                SET operational_state = 'ACTIVE'
                FROM parking_zones pz, parking_floors pf
                WHERE ps.zone_id = pz.id
                  AND pz.floor_id = pf.id
                  AND pf.facility_id = :facilityId
                  AND pf.floor_number = :floorNumber
                  AND pz.code = :zoneCode
                  AND ps.space_number = :spaceNumber
                """)
                .param("facilityId", FACILITY_ID)
                .param("floorNumber", floorNumber)
                .param("zoneCode", zoneCode)
                .param("spaceNumber", spaceNumber)
                .update();
        assertEquals(1, changed);
    }
}
