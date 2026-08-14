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
| Parking sessions | Entry, active stay, exit, and vehicle history |
| Reservations | Future capacity claims and arrival handling |
| Pricing | Rate plans, fee calculation, and adjustments |
| Identity | Operator identity, roles, and access policy |
| Audit | Append-only record of sensitive and operational actions |
| Reporting | Occupancy, turnover, utilization, and operational views |

A module owns its tables and exposes behaviour through application interfaces. Direct access to another module's
persistence model is not allowed.

## Allocation model

A parking request contains a facility, vehicle identifier, and required size class. Candidate spaces are ordered by
floor number, zone code, and space number. The allocation domain selects the first active, compatible, unoccupied space.

The allocation application interface provides transactional park, find, and unpark operations. PostgreSQL stores
assignment and release state so it survives application restarts. Partial unique indexes enforce one active assignment
per vehicle within a facility and one active assignment per space.

Park operations lock candidate space rows with `FOR UPDATE SKIP LOCKED`. Selection and insertion share one transaction,
and transient database failures retry the whole transaction at most three times. PostgreSQL concurrency tests cover
competing requests for the same vehicle and the same remaining space.

The framework-free `AllocationManager` continues to hold availability calculations and domain tests. Parking sessions,
request idempotency, and the public API remain later milestones.

## Data

PostgreSQL is the system of record. Flyway migrations own schema changes. Reference data for the 7,200-space facility is
created through a local-only development fixture, not application startup code.

Redis is not part of the initial baseline. It will be introduced only if measured caching, coordination, or delivery
requirements justify the additional failure mode.

## API and user interface

The planned backend API is versioned REST documented with OpenAPI. Mutation requests will use idempotency keys where
clients may retry. Errors will use one stable machine-readable format.

The planned React and TypeScript application will support operator workflows and live occupancy. Live updates will use
server-sent events unless bidirectional communication becomes necessary.

## Security

Authentication will use OpenID Connect. Authorization will be role-based at the API boundary and reinforced within
sensitive application operations. Administrative changes, manual overrides, and pricing adjustments will be written to
the audit log.

Personal data is limited to what is required to identify a vehicle and parking session. Retention rules will be
documented before production readiness.

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
- [Persistence](persistence.md)
