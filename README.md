# Smart Parking Tower Manager

Smart Parking Tower Manager is a modular parking-operations platform built around a deterministic 7,200-space reference
facility. The implementation includes a Spring Boot backend, facility and allocation domains, PostgreSQL persistence,
Flyway migrations, and Docker-backed integration tests.

## Reference facility

The maintained fixture contains:

- 6 floors
- Zones A through F on each floor
- 200 spaces per zone
- 7,200 spaces in total
- 100 small, 80 medium, and 20 large spaces per zone

Allocation selects the lowest compatible floor, then the first zone alphabetically, then the lowest space number. Park,
find, and unpark operations persist active assignments transactionally. PostgreSQL row locks coordinate concurrent
allocation, and database indexes enforce one active assignment per vehicle and per space.

## Architecture

The system is a modular monolith. Domain rules remain independent of Spring and persistence frameworks. PostgreSQL is
the system of record, and each module owns its schema and application interfaces.

| Area | Current implementation |
| --- | --- |
| Backend | Java 21, Spring Boot 4.1 |
| Facility domain | Facilities, floors, zones, spaces, compatibility, operational state |
| Allocation domain | Deterministic selection, park, find, unpark, availability, uniqueness rules |
| Allocation persistence | Atomic assignment and release, row locking, bounded transient retries |
| Persistence | PostgreSQL, Flyway schema, local reference fixture |
| Verification | JUnit, Testcontainers concurrency tests, Checkstyle, GitHub Actions |
| Operations | Health, liveness, readiness, graceful shutdown |

The public REST API, parking sessions, React interface, reservations, pricing, identity, audit, and production deployment
remain planned. Availability calculations currently use the in-memory domain model and are not exposed through an
application API.

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
- [Persistence](docs/architecture/persistence.md)
- [Transactional locking decision](docs/decisions/0002-use-postgresql-row-locks-for-allocation.md)
- [Delivery roadmap](docs/roadmap.md)
- [Contribution guide](CONTRIBUTING.md)
- [Security policy](SECURITY.md)
- [Documentation standard](docs/standards/documentation.md)

## Licence

No licence has been selected. All rights remain with the repository owner until a licence file is added.
