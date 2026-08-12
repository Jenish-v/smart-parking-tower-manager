# Backend

The backend is a Java 21 and Spring Boot service. It owns the parking domain, public API, persistence, and operational
endpoints.

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

The fixture creates six floors, zones A through F on each floor, and 200 spaces per zone. Each zone contains 100 small,
80 medium, and 20 large spaces. The fixture is excluded from the default profile.

## Verify

Run the complete backend verification from this directory:

```bash
mvn verify
```

The build enforces the Java and Maven versions, runs Checkstyle, compiles the application, and runs the test suite.
Docker-backed tests verify the Flyway schema and reference fixture against PostgreSQL. Testcontainers skips those tests
when Docker is unavailable; continuous integration runs them with Docker available.

## Run

Start PostgreSQL with the database and credentials shown above, then run:

```bash
mvn spring-boot:run
```

The service listens on port 8080 and exposes:

```text
GET /actuator/health
GET /actuator/health/liveness
GET /actuator/health/readiness
```

Only health and application information are exposed through the Actuator web interface. Additional operational
endpoints require an explicit architecture and security review.
