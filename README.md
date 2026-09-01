# Smart Parking Tower Manager

Smart Parking Tower Manager is a modular parking-operations platform built around a deterministic 7,200-space reference
facility. The implementation includes a Spring Boot backend, a React operator shell, facility, allocation,
parking-session, and reservation-domain modules, a versioned REST API, PostgreSQL persistence, Flyway migrations, and
automated backend and frontend checks.

## Reference facility

The maintained fixture contains:

- 6 floors
- Zones A through F on each floor
- 200 spaces per zone
- 7,200 spaces in total
- 100 small, 80 medium, and 20 large spaces per zone

Allocation selects the lowest compatible floor, then the first zone alphabetically, then the lowest space number. Entry
and exit commands atomically coordinate parking sessions with allocation. Entry also fulfills and links a matching
reservation inside its arrival window. Request identifiers make command retries idempotent, while PostgreSQL constraints
and row locks protect active vehicles and spaces under concurrency.

## Architecture

The system is a modular monolith. Domain rules remain independent of Spring and persistence frameworks. PostgreSQL is
the system of record, and each backend module owns its schema and application interfaces. The browser application calls
the public API through a typed transport boundary.

| Area | Current implementation |
| --- | --- |
| Backend | Java 21, Spring Boot 4.1 |
| Frontend | React 19, TypeScript, Vite, responsive operator layout and routing |
| Facility domain | Facilities, floors, zones, spaces, compatibility, operational state |
| Allocation | Deterministic selection, persisted assignments, occupancy snapshots, row locking, bounded retries |
| Parking sessions | Idempotent entry and exit, active lookup, immutable session history |
| Reservations | Capacity-safe claims, API and dashboard workflows, and atomic arrival fulfillment |
| API | Versioned REST endpoints, OpenAPI 3.0 contract, RFC 9457 problem responses |
| Persistence | PostgreSQL, Flyway schema, local reference fixture |
| Verification | JUnit, Vitest, Testing Library, Testcontainers, ESLint, Checkstyle, GitHub Actions |
| Local runtime | Docker Compose with PostgreSQL, backend, and frontend health checks |
| Operations | Health, liveness, readiness, graceful shutdown |

The dashboard presents current facility and floor occupancy with server-sent updates, manual refresh, and 15-second
fallback polling. It supports parking entry, exit, vehicle session search, and reservation creation, history, and
cancellation against the public API. PostgreSQL serializes reservation capacity claims, protects nested size pools, and
atomically fulfills a matching claim during parking entry. Pricing, identity, audit, and production deployment remain
planned. Session and idempotency history is retained without automated deletion until a production retention policy is
approved.

The API and dashboard do not yet authenticate or authorize callers. They are suitable for development and contract
integration, not internet-facing production deployment.

## Repository layout

```text
backend/        Spring Boot service, migrations, API contract, and backend tests
frontend/       React operator dashboard, typed API client, and component tests
docs/           Architecture, API guidance, decisions, standards, and roadmap
.github/        Continuous integration and repository templates
```

## Run the complete local stack

Docker Engine with Compose v2 is the only requirement for the maintained local runtime. From the repository root:

```bash
docker compose up --build
```

The operator dashboard is available at `http://localhost:5173`, the backend at `http://localhost:8080`, and the OpenAPI
contract at `http://localhost:8080/openapi.yaml`. The local profile loads the deterministic 7,200-space reference
facility. Stop the services with `docker compose down`. Add `--volumes` only when the local database should be erased.
Continuous integration builds this stack, waits for every health check, verifies the reference-facility occupancy, and
loads the operator dashboard before runtime changes can be merged.

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

Run the service with the 7,200-space development fixture:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

The service listens on port 8080. Health endpoints are under `/actuator/health`, and the OpenAPI contract is available
at `/openapi.yaml`.

## Frontend setup

Requirements are Node.js 22.12 or newer and npm 11 or newer. From `frontend/`, install dependencies and run the
development server:

```bash
npm ci
npm run dev
```

The dashboard listens on port 5173. Its development server proxies API requests to the backend on port 8080. It uses the
reference facility by default; set `VITE_FACILITY_ID` to target another configured facility. Run the frontend lint,
test, type-check, and production-build sequence with:

```bash
npm run check
```

See [frontend/README.md](frontend/README.md) for configuration and individual commands.

## API surface

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/v1/facilities/{facilityId}/parking-sessions/entries` | Start or replay entry |
| POST | `/api/v1/facilities/{facilityId}/parking-sessions/exits` | Complete or replay exit |
| GET | `/api/v1/facilities/{facilityId}/parking-sessions/active` | Find the active session |
| GET | `/api/v1/facilities/{facilityId}/parking-sessions` | List vehicle history |
| GET | `/api/v1/facilities/{facilityId}/occupancy` | Get facility and floor occupancy snapshot |
| GET | `/api/v1/facilities/{facilityId}/occupancy/stream` | Stream changed occupancy snapshots |
| PUT | `/api/v1/facilities/{facilityId}/reservations/{reservationId}` | Create or replay a reservation |
| GET | `/api/v1/facilities/{facilityId}/reservations/{reservationId}` | Get a reservation |
| DELETE | `/api/v1/facilities/{facilityId}/reservations/{reservationId}` | Cancel or replay cancellation |
| GET | `/api/v1/facilities/{facilityId}/reservations` | List reservation history for a vehicle |

Parking-session mutations require an `Idempotency-Key` header containing a UUID. Reservation creation uses the
client-selected UUID in the request path as its replay key. For example:

```bash
curl -i http://localhost:8080/api/v1/facilities/d936bb7d-3027-47aa-a47b-d04a37e07310/parking-sessions/entries \
  -X POST \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 3d30408d-7c3a-4f61-9138-3b9b728782da' \
  -d '{"vehicleIdentifier":"TOR 501","requiredSize":"SMALL"}'
```

See [backend/README.md](backend/README.md) for runtime details and [docs/api/README.md](docs/api/README.md) for request,
response, idempotency, and error behaviour.

## Documentation

- [Architecture overview](docs/architecture/overview.md)
- [Facility module](docs/architecture/facility-module.md)
- [Allocation domain](docs/architecture/allocation-domain.md)
- [Parking sessions](docs/architecture/parking-sessions.md)
- [Reservation domain](docs/architecture/reservation-domain.md)
- [Occupancy reporting](docs/architecture/occupancy-reporting.md)
- [Frontend component standards](docs/frontend/component-standards.md)
- [API guide](docs/api/README.md)
- [OpenAPI contract](backend/src/main/resources/static/openapi.yaml)
- [Persistence](docs/architecture/persistence.md)
- [Transactional locking decision](docs/decisions/0002-use-postgresql-row-locks-for-allocation.md)
- [Delivery roadmap](docs/roadmap.md)
- [Contribution guide](CONTRIBUTING.md)
- [Security policy](SECURITY.md)
- [Documentation standard](docs/standards/documentation.md)

## Licence

No licence has been selected. All rights remain with the repository owner until a licence file is added.
