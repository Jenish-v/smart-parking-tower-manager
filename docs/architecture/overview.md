# Architecture Overview

## System context

Smart Parking Tower Manager operates a configurable, multi-level parking facility. The reference installation has six floors, six zones per floor, and 200 spaces per zone. Operators use the system to admit vehicles, locate them, complete exits, inspect capacity, manage reservations, and investigate operational history.

The initial deployment is designed for one facility while keeping facility identity explicit in the model. Multi-facility operation can be added without rewriting the allocation domain.

## Architectural style

The first production release is a modular monolith. One deployable backend contains modules with explicit ownership and restricted dependencies. A separate browser application consumes the public API.

This structure keeps transactions and operational deployment simple while preserving boundaries that can support later extraction. Distribution is not a goal until scale or organizational ownership provides evidence for it.

## Modules

| Module | Responsibility |
| --- | --- |
| Facility | Floors, zones, spaces, compatibility, operational state |
| Allocation | Candidate selection and atomic space assignment |
| Parking sessions | Entry, active stay, exit, and vehicle lookup |
| Reservations | Future capacity claims and arrival handling |
| Pricing | Rate plans, fee calculation, and adjustments |
| Identity | Operator identity, roles, and access policy |
| Audit | Append-only record of sensitive and operational actions |
| Reporting | Occupancy, turnover, utilization, and operational views |

A module owns its tables and exposes behaviour through application interfaces. Direct access to another module's persistence model is not allowed.

## Allocation model

A parking request contains a facility, vehicle identifier, and required size class. Candidate spaces are ordered by floor number, zone code, and space number. The allocation transaction selects the first compatible available space and marks it occupied before the transaction commits.

Correctness depends on both application logic and database enforcement:

- one active parking session per vehicle
- one active parking session per space
- atomic transition from available to occupied
- idempotent handling of repeated entry and exit requests
- consistent lock ordering under concurrent allocation

The implementation will verify the locking strategy with integration and concurrency tests against PostgreSQL.

## Data

PostgreSQL is the system of record. Flyway migrations own schema changes. Reference data for the 7,200-space facility is created through repeatable development fixtures, not application startup side effects.

Redis is not part of the initial baseline. It will be introduced only if measured caching, coordination, or delivery requirements justify the additional failure mode.

## API and user interface

The backend exposes a versioned REST API documented with OpenAPI. Mutation requests use idempotency keys where clients may retry. Errors use one stable machine-readable format.

The React and TypeScript application supports operator workflows and live occupancy. Live updates will use server-sent events unless bidirectional communication becomes necessary.

## Security

Authentication will use OpenID Connect. Authorization is role-based at the API boundary and reinforced within sensitive application operations. Administrative changes, manual overrides, and pricing adjustments are written to the audit log.

Personal data is limited to what is required to identify a vehicle and parking session. Retention rules will be documented before production readiness.

## Operations

The application will provide:

- startup, readiness, and liveness health endpoints
- structured logs with request and correlation identifiers
- metrics for allocation latency, occupancy, failures, and database health
- trace propagation across HTTP and database operations
- graceful shutdown that stops new work before terminating active requests

Local development uses Docker Compose. Production packaging uses immutable container images and external configuration.

## Design targets

The initial performance test profile will use the 7,200-space reference facility and concurrent entry traffic. The allocation path targets a 250 ms p95 response time under the documented baseline load. Correctness targets, including duplicate assignment, are absolute and take precedence over latency.

Availability and recovery targets will be fixed when deployment topology is selected. They will not be claimed from local test results.

## Related decisions

- [ADR-0001: Use a modular monolith](../decisions/0001-use-modular-monolith.md)
