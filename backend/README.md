# Backend

The backend is a Java 21 and Spring Boot service. It owns the parking domain, public API, persistence, and operational endpoints.

## Requirements

- Java 21
- Maven 3.6.3 or newer

## Verify

Run the complete backend verification from this directory:

```bash
mvn verify
```

The build enforces the Java and Maven versions, runs Checkstyle, compiles the application, and runs the test suite.

## Run

```bash
mvn spring-boot:run
```

The service listens on port 8080. The baseline exposes these operational endpoints:

```text
GET /actuator/health
GET /actuator/health/liveness
GET /actuator/health/readiness
```

Only health and application information are exposed through the Actuator web interface. Additional operational endpoints require an explicit architecture and security review.

## Configuration

Configuration follows Spring Boot's external configuration model. Secrets and environment-specific values do not belong in committed YAML files.

The database configuration will be introduced with the facility model and its first schema migration.
