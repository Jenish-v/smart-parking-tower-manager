# Smart Parking Tower Manager

Smart Parking Tower Manager is a modular parking-operations platform built around a deterministic 7,200-space reference
facility. The implementation includes a Spring Boot backend, facility, allocation, and parking-session modules,
PostgreSQL persistence, Flyway migrations, and Docker-backed integration tests.

## Reference facility

The maintained fixture contains:

- 6 floors
- Zones A through F on each floor
- 200 spaces per zone
- 7,200 spaces in total
- 100 small, 80 medium, and 20 large spaces per zone

Allocation selects the lowest compatible floor, then the first zone alphabetically, then the lowest space number. Entry
and exit commands atomically coordinate parking sessions with allocation. Request identifiers make command retries
idempotent, while PostgreSQL constraints and row locks protect active vehicles and spaces under concurrency.

## Architecture

The system is a modular monolith. Domain rules remain independent of Spring and persistence frameworks. PostgreSQL is
the system of record, and each module owns its schema and application interfaces.

| Area | Current implementation |
| --- | --- |
| Backend | Java 21, Spring Boot 4.1 |
| Facility domain | Facilities, floors, zones, spaces, compatibility, operational state |
| Allocation | Deterministic selection, persisted assignments, row locking, bounded transient retries |
| Parking sessions | Idempotent entry and exit, active lookup, immutable session history |
| Persistence | PostgreSQL, Flyway schema, local reference fixture |
| Verification | JUnit, Testcontainers concurrency tests, Checkstyle, GitHub Actions |
| Operations | Health, liveness, readiness, graceful shutdown |

The public REST API, React interface, reservations, pricing, identity, audit, and production deployment remain planned.
Availability calculations currently use the in-memory allocation model and are not exposed through an application API.
Session and idempotency history is retained without automated deletion until a production retention policy is approved.

## Repository layout

```text
backend/        Spring Boot service, migrations, and backend tests
docs/           Architecture, decisions, standards, and roadmap
.github/        Continuous integration and repository templates
```

Directories are added when their first maintained artifact is introduced.

## Backend setup

Requirements:

- Java 21
- Maven 3.6.3 or newer
- PostgreSQL 17
- Docker to run the integration tests

From `backend/`, configure the database if it differs from the local defaults:

```text
DB_URL=jdbc:postgresql://localhost:5432/smart_parking
DB_USERNAME=smart_parking
DB_PASSWORD=smart_parking
```

Run the complete backend verification:

```bash
mvn verify
```

Run the service against an existing PostgreSQL database:

```bash
mvn spring-boot:run
```

Add `-Dspring-boot.run.profiles=local` to load the 7,200-space development fixture. The service exposes health endpoints
under `/actuator/health`; no parking-operation API is available yet.

See [backend/README.md](backend/README.md) for configuration and verification details.

## Documentation

- [Architecture overview](docs/architecture/overview.md)
- [Facility module](docs/architecture/facility-module.md)
- [Allocation domain](docs/architecture/allocation-domain.md)
- [Parking sessions](docs/architecture/parking-sessions.md)
- [Persistence](docs/architecture/persistence.md)
- [Transactional locking decision](docs/decisions/0002-use-postgresql-row-locks-for-allocation.md)
- [Delivery roadmap](docs/roadmap.md)
- [Contribution guide](CONTRIBUTING.md)
- [Security policy](SECURITY.md)
- [Documentation standard](docs/standards/documentation.md)

## Licence

No licence has been selected. All rights remain with the repository owner until a licence file is added.
