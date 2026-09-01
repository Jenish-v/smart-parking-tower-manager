package com.jenish.smartparking.parkingsession.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
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
class ParkingSessionApiTest {

    private static final UUID FACILITY_ID =
            UUID.fromString("d936bb7d-3027-47aa-a47b-d04a37e07310");

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
        jdbcClient.sql("DELETE FROM parking_session_requests").update();
        jdbcClient.sql("DELETE FROM parking_sessions").update();
        jdbcClient.sql("DELETE FROM reservations").update();
        jdbcClient.sql("DELETE FROM active_allocations").update();
        jdbcClient.sql("UPDATE parking_spaces SET operational_state = 'OUT_OF_SERVICE'").update();
    }

    @Test
    void exposesIdempotentEntryExitLookupAndHistory() throws Exception {
        activateSpace(1, "A", 1);
        UUID entryKey = UUID.randomUUID();

        HttpResponse<String> entered = post(
                "/entries",
                entryKey,
                """
                {"vehicleIdentifier":"tor 501","requiredSize":"SMALL"}
                """);
        HttpResponse<String> entryReplay = post(
                "/entries",
                entryKey,
                """
                {"vehicleIdentifier":"tor 501","requiredSize":"SMALL"}
                """);

        assertEquals(201, entered.statusCode());
        assertEquals(entered.body(), entryReplay.body());
        assertTrue(entered.body().contains("\"vehicleIdentifier\":\"TOR 501\""));
        assertTrue(entered.body().contains("\"status\":\"ACTIVE\""));

        HttpResponse<String> active = get("/active?vehicleIdentifier=TOR%20501");
        assertEquals(200, active.statusCode());
        assertEquals(entered.body(), active.body());

        HttpResponse<String> exited = post(
                "/exits",
                UUID.randomUUID(),
                """
                {"vehicleIdentifier":"TOR 501"}
                """);
        assertEquals(200, exited.statusCode());
        assertTrue(exited.body().contains("\"status\":\"COMPLETED\""));

        HttpResponse<String> history = get("?vehicleIdentifier=TOR%20501");
        assertEquals(200, history.statusCode());
        assertTrue(history.body().startsWith("[{"));
        assertTrue(history.body().contains("\"status\":\"COMPLETED\""));
    }

    @Test
    void returnsStableValidationAndConflictProblems() throws Exception {
        activateSpace(1, "A", 1);

        HttpResponse<String> invalid = post(
                "/entries",
                UUID.randomUUID(),
                """
                {"vehicleIdentifier":"","requiredSize":"SMALL"}
                """);
        assertEquals(400, invalid.statusCode());
        assertProblem(invalid, "VALIDATION_FAILED");

        post(
                "/entries",
                UUID.randomUUID(),
                """
                {"vehicleIdentifier":"TOR 502","requiredSize":"SMALL"}
                """);
        HttpResponse<String> conflict = post(
                "/entries",
                UUID.randomUUID(),
                """
                {"vehicleIdentifier":"TOR 502","requiredSize":"SMALL"}
                """);
        assertEquals(409, conflict.statusCode());
        assertProblem(conflict, "ACTIVE_SESSION_EXISTS");
    }

    @Test
    void returnsNotFoundForMissingActiveSession() throws Exception {
        HttpResponse<String> response = get("/active?vehicleIdentifier=UNKNOWN");

        assertEquals(404, response.statusCode());
        assertProblem(response, "ACTIVE_SESSION_NOT_FOUND");
    }

    @Test
    void fulfillsAReservationThroughTheEntryEndpoint() throws Exception {
        activateSpace(1, "A", 1);
        UUID reservationId = insertConfirmedReservation("TOR 504", "SMALL");

        HttpResponse<String> entered = post(
                "/entries",
                UUID.randomUUID(),
                """
                {"vehicleIdentifier":"TOR 504","requiredSize":"SMALL"}
                """);

        assertEquals(201, entered.statusCode());
        assertTrue(entered.body().contains("\"reservationId\":\"" + reservationId + "\""));
        assertEquals(
                "FULFILLED",
                jdbcClient.sql("SELECT status FROM reservations WHERE id = :id")
                        .param("id", reservationId)
                        .query(String.class)
                        .single());
    }

    @Test
    void publishesTheOpenApiContract() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/openapi.yaml")).GET().build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("openapi: 3.0.3"));
        assertTrue(response.body().contains("operationId: enterVehicle"));
    }

    private HttpResponse<String> post(
            String suffix,
            UUID idempotencyKey,
            String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(sessionUri(suffix))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey.toString())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String suffix) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(sessionUri(suffix)).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI sessionUri(String suffix) {
        return uri("/api/v1/facilities/" + FACILITY_ID + "/parking-sessions" + suffix);
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private void assertProblem(HttpResponse<String> response, String code) {
        assertTrue(response.headers()
                .firstValue("Content-Type")
                .orElseThrow()
                .startsWith("application/problem+json"));
        assertTrue(response.body().contains("\"code\":\"" + code + "\""));
        assertTrue(response.body().contains("\"status\":" + response.statusCode()));
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

    private UUID insertConfirmedReservation(String vehicleIdentifier, String requiredSize) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        UUID reservationId = UUID.randomUUID();
        jdbcClient.sql("""
                INSERT INTO reservations (
                    id, facility_id, vehicle_identifier, required_size,
                    starts_at, ends_at, created_at, status
                ) VALUES (
                    :id, :facilityId, :vehicleIdentifier, :requiredSize,
                    :startsAt, :endsAt, :createdAt, 'CONFIRMED'
                )
                """)
                .param("id", reservationId)
                .param("facilityId", FACILITY_ID)
                .param("vehicleIdentifier", vehicleIdentifier)
                .param("requiredSize", requiredSize)
                .param("startsAt", OffsetDateTime.ofInstant(
                        now.minus(5, ChronoUnit.MINUTES),
                        ZoneOffset.UTC))
                .param("endsAt", OffsetDateTime.ofInstant(
                        now.plus(1, ChronoUnit.HOURS),
                        ZoneOffset.UTC))
                .param("createdAt", OffsetDateTime.ofInstant(
                        now.minus(10, ChronoUnit.MINUTES),
                        ZoneOffset.UTC))
                .update();
        return reservationId;
    }
}
