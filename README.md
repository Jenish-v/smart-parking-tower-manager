# Smart Parking Tower Manager

Smart Parking Tower Manager is a production-oriented platform for operating a multi-level parking facility. The system will manage space allocation, vehicle movement, occupancy, reservations, pricing, and operator workflows from a single source of truth.

The reference facility contains 7,200 parking spaces:

- 6 floors
- 6 zones per floor
- 200 spaces per zone
- Small, medium, and large space classes

## Project status

The project is in repository-foundation stage. Architecture and delivery standards are being established before application code is introduced. Features described as planned are not yet implemented.

## Core rules

The first implementation will preserve the original parking model:

- Assign the lowest compatible floor first.
- Within a floor, assign the first compatible zone alphabetically.
- Prevent a vehicle from occupying more than one space.
- Maintain direct vehicle-to-space and space-to-vehicle lookups.
- Support park, unpark, find, and availability operations.
- Keep allocation operations safe under concurrent requests.

## Target architecture

The system will use a modular monolith for the initial production release.

| Area | Technology |
| --- | --- |
| Backend | Java 21, Spring Boot |
| Data | PostgreSQL, Flyway |
| Frontend | React, TypeScript |
| API | REST, OpenAPI |
| Local environment | Docker Compose |
| Testing | JUnit, Testcontainers, frontend component tests |
| Delivery | GitHub Actions |
| Observability | Structured logs, metrics, health endpoints |

Architecture decisions and boundaries are recorded under `docs/`.

## Planned capabilities

- Facility, floor, zone, and space configuration
- Deterministic space allocation
- Entry and exit processing
- Live occupancy views
- Vehicle search
- Reservations
- Pricing rules and parking sessions
- Operator and administrator access
- Audit history
- Operational metrics
- Simulation tools for load and concurrency testing

## Repository layout

```text
backend/        Spring Boot application
frontend/       Operator dashboard
docs/           Architecture, decisions, operations, and delivery notes
infra/          Local and deployment infrastructure
scripts/        Repeatable development and maintenance commands
```

Directories are added when their first maintained artifact is introduced.

## Documentation

Start with:

- `docs/architecture/overview.md`
- `docs/roadmap.md`
- `CONTRIBUTING.md`
- `SECURITY.md`

## Development

Local setup instructions will be published with the first executable application slice. The repository will not advertise commands that do not work.

## Licence

No licence has been selected yet. All rights remain with the repository owner until a licence file is added.
