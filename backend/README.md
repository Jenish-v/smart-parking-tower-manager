# Backend

The backend is a Java 21 and Spring Boot service. It owns the parking domain, versioned REST API, persistence, and
operational endpoints.

## Requirements

- Java 21
- Maven 3.6.3 or newer
- Docker for PostgreSQL integration tests and local database use

## Configuration

The service uses PostgreSQL. The default development connection is:

```text
DB_URL=jdbc:postgresql://localhost:5432/smart_parking
DB_USERNAME=smart_parking
DB_PASSWORD=smart_parking
```

Override every value through environment variables outside local development. Flyway applies the production schema
from `src/main/resources/db/migration`.

Activate the `local` profile to also load the 7,200-space reference fixture:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

The maintained root `compose.yaml` activates this profile automatically and waits for PostgreSQL readiness before
starting the service.

The fixture creates six floors, zones A through F on each floor, and 200 spaces per zone. Each zone contains 100 small,
80 medium, and 20 large spaces. The fixture is excluded from the default profile.

## Verify

Run the complete backend verification from this directory:

```bash
mvn verify
```

The build enforces the Java and Maven versions, runs Checkstyle, compiles the application, and runs the test suite.
Docker-backed tests apply all Flyway migrations, load the reference fixture, and exercise HTTP validation, occupancy
reporting, server-sent delivery, idempotent parking-session commands, concurrent allocation, and reservation capacity
claims against PostgreSQL. The suite also covers reservation cancellation, expiry, lifecycle transitions, and arrival
matching, including rollback when a matched arrival cannot obtain a space.
Testcontainers skips those tests when Docker is unavailable; continuous integration runs them with Docker available.

## Run

Start PostgreSQL with the database and credentials shown above, then run:

```bash
mvn spring-boot:run
```

The service listens on port 8080 and exposes:

```text
GET  /actuator/health
GET  /actuator/health/liveness
GET  /actuator/health/readiness
GET  /openapi.yaml
GET  /api/v1/facilities/{facilityId}/occupancy
GET  /api/v1/facilities/{facilityId}/occupancy/stream
POST /api/v1/facilities/{facilityId}/parking-sessions/entries
POST /api/v1/facilities/{facilityId}/parking-sessions/exits
GET  /api/v1/facilities/{facilityId}/parking-sessions/active?vehicleIdentifier={value}
GET  /api/v1/facilities/{facilityId}/parking-sessions?vehicleIdentifier={value}
PUT  /api/v1/facilities/{facilityId}/reservations/{reservationId}
GET  /api/v1/facilities/{facilityId}/reservations/{reservationId}
DELETE /api/v1/facilities/{facilityId}/reservations/{reservationId}
GET  /api/v1/facilities/{facilityId}/reservations?vehicleIdentifier={value}
```

Parking-session mutations require a UUID `Idempotency-Key` header. Reservation creation uses a client-selected UUID in
the path and safely replays an identical request. Errors use `application/problem+json` and include a stable `code`
property. The API currently has no authentication or authorization and must not be exposed as a production internet
endpoint. Additional Actuator endpoints require an explicit architecture and security review.
