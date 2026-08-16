# Architecture Overview

## System context

Smart Parking Tower Manager operates a configurable, multi-level parking facility. The reference installation has six
floors, six zones per floor, and 200 spaces per zone. Operators use the system to admit vehicles, locate them, complete
exits, inspect capacity, manage reservations, and investigate operational history.

The initial deployment is designed for one facility while keeping facility identity explicit in the model.
Multi-facility operation can be added without rewriting the allocation domain.

## Architectural style

The first production release is a modular monolith. One deployable backend contains modules with explicit ownership and
restricted dependencies. A separate browser application consumes the public API.

This structure keeps transactions and operational deployment simple while preserving boundaries that can support later
extraction. Distribution is not a goal until scale or organizational ownership provides evidence for it.

## Modules

| Module | Responsibility |
| --- | --- |
| Facility | Floors, zones, spaces, compatibility, operational state |
| Allocation | Candidate selection and active vehicle-to-space assignment |
| Parking sessions | Idempotent entry, active stay, exit, and vehicle history |
| Reservations | Future capacity claims and arrival handling |
| Pricing | Rate plans, fee calculation, and adjustments |
| Identity | Operator identity, roles, and access policy |
| Audit | Append-only record of sensitive and operational actions |
| Reporting | Occupancy, turnover, utilization, and operational views |

A module owns its tables and exposes behaviour through application interfaces. Direct access to another module's
persistence model is not allowed.

## Parking workflow

A parking request contains a request identifier, facility, vehicle identifier, and required size class. The
parking-session module coordinates the lifecycle through the allocation application interface.

Entry atomically selects a compatible space and starts one active session. Exit atomically releases the allocation and
completes that session. PostgreSQL stores both modules' state so it survives application restarts. Request identifiers
make matching entry and exit retries idempotent, including retries processed by another application instance.

Allocation candidates are ordered by floor number, zone code, and space number. Park operations lock candidate rows with
`FOR UPDATE SKIP LOCKED`; partial indexes enforce one active assignment and session per vehicle and space. PostgreSQL
concurrency tests cover competing allocation and idempotent session requests.

The framework-free `AllocationManager` continues to hold availability calculations and domain tests. Reservations
remain a later milestone.

## Data

PostgreSQL is the system of record. Flyway migrations own schema changes. Reference data for the 7,200-space facility is
created through a local-only development fixture, not application startup code.

Redis is not part of the initial baseline. It will be introduced only if measured caching, coordination, or delivery
requirements justify the additional failure mode.

Completed sessions and their request records are retained without automated deletion. A production retention and
deletion policy for vehicle identifiers remains required before deployment.

## API and user interface

The backend exposes versioned REST endpoints for entry, exit, active lookup, and vehicle history. The HTTP adapter calls
the parking-session application interface and does not access persistence implementations. The OpenAPI 3.0 contract is
served at `/openapi.yaml`.

Errors use RFC problem details with stable codes. Mutation commands require UUID idempotency keys. Request validation
runs at the HTTP boundary before domain construction.

The planned React and TypeScript application will consume this API. Live updates will use server-sent events unless
bidirectional communication becomes necessary.

## Security

The API currently has no authentication or authorization. It must remain in a trusted development environment until the
identity milestone implements and verifies those boundaries.

Personal data is limited to what is required to identify a vehicle and parking session. Retention rules will be
approved before production readiness.

## Operations

The application currently provides startup, readiness, and liveness health endpoints and graceful shutdown. Planned
operational work includes structured logs, correlation identifiers, allocation and database metrics, and trace
propagation.

Local development uses PostgreSQL and Maven. Production packaging and deployment topology remain undecided.

## Design targets

The initial performance test profile will use the 7,200-space reference facility and concurrent entry traffic. The
allocation path targets a 250 ms p95 response time under the documented baseline load. Correctness targets, including
duplicate assignment, are absolute and take precedence over latency.

Availability and recovery targets will be fixed when deployment topology is selected. They will not be claimed from
local test results.

## Related decisions

- [ADR-0001: Use a modular monolith](../decisions/0001-use-modular-monolith.md)
- [ADR-0002: Use PostgreSQL row locks for allocation](../decisions/0002-use-postgresql-row-locks-for-allocation.md)
- [Facility module](facility-module.md)
- [Allocation domain](allocation-domain.md)
- [Parking sessions](parking-sessions.md)
- [API guide](../api/README.md)
- [Persistence](persistence.md)
