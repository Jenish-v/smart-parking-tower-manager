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
| Pricing | Versioned rate plans, exact fee calculation, and adjustments |
| Identity | Operator identity, roles, and access policy |
| Audit | Append-only record of sensitive and operational actions |
| Reporting | Occupancy, turnover, utilization, and operational views |

A module owns its tables and exposes behaviour through application interfaces. Direct access to another module's
persistence model is not allowed.

## Parking workflow

A parking request contains a request identifier, facility, vehicle identifier, and required size class. The
parking-session module coordinates the lifecycle through the allocation application interface.

Entry atomically fulfills a matching reservation, selects a compatible space, and starts one active session. The
session retains the reservation identifier. Exit atomically releases the allocation and completes that session.
PostgreSQL stores each module's state so it survives application restarts. Request identifiers make matching entry and
exit retries idempotent, including retries processed by another application instance.

Allocation candidates are ordered by floor number, zone code, and space number. Park operations lock candidate rows with
`FOR UPDATE SKIP LOCKED`; partial indexes enforce one active assignment and session per vehicle and space. PostgreSQL
concurrency tests cover competing allocation and idempotent session requests.

The framework-free `AllocationManager` continues to hold availability calculations and domain tests.

## Reservation workflow

The reservation domain models a future capacity claim for one facility, vehicle, and required size. Confirmed
reservations use half-open arrival windows: the start is eligible and the end is not. A confirmed reservation can be
cancelled before its window ends, fulfilled by a matching arrival inside the window, or expired when the window ends.
Terminal states cannot transition again.

PostgreSQL persists reservations and serializes facility-scoped capacity decisions. Capacity checks protect nested
size-compatible pools across every point in the requested window. The public API supports idempotent creation by a
client-selected UUID, lookup, vehicle history, and repeat-safe cancellation. The dashboard exposes those workflows.
Parking entry row-locks and fulfills a matching claim in the allocation and session transaction; allocation failure
rolls the transition back. Expired reservations transition on access. A scheduled expiry job remains operational
hardening work. The domain does not reserve or allocate a physical space.

## Pricing workflow

The pricing domain represents immutable rate-plan versions with a half-open effective window, grace period, billing
increment, and a size-specific charge and rolling 24-hour cap. All size bands in a plan use one currency. Fee
calculation uses minor currency units, rounds billable time up to whole increments, and applies the cap separately to
each rolling day. Quotes retain the plan identifier and version so a future receipt can reproduce the assessment.

PostgreSQL stores rate-plan versions and prevents overlapping effective windows. Parking-session exit selects the plan
effective at entry time, calculates the fee, and stores an immutable receipt before the outer transaction commits.
Failure leaves the session and allocation active. The exit response includes the receipt breakdown, and an exact retry
returns the same receipt. The dashboard presents the immediate receipt total and reference. Manual adjustments and
receipt-history presentation remain Milestone 11 work.

## Data

PostgreSQL is the system of record. Flyway migrations own schema changes. Reference data for the 7,200-space facility is
created through a local-only development fixture, not application startup code.

Redis is not part of the initial baseline. It will be introduced only if measured caching, coordination, or delivery
requirements justify the additional failure mode.

Completed sessions and their request records are retained without automated deletion. A production retention and
deletion policy for vehicle identifiers remains required before deployment.

## API and user interface

The backend exposes versioned REST endpoints for parking entry, exit, active lookup, vehicle history, occupancy, and
reservation operations. HTTP adapters call application interfaces and do not access persistence implementations. The
OpenAPI 3.0 contract is served at `/openapi.yaml`.

Errors use RFC problem details with stable codes. Parking-session mutations require UUID idempotency headers;
reservation creation uses the client-selected path UUID for replay protection. Request validation runs at the HTTP
boundary before domain construction.

The React and TypeScript application is a separate module that consumes only the public HTTP contract. It provides the
responsive operator shell, entry and exit commands, vehicle session search, and reservation creation, history, and
cancellation through typed API clients. Route-level pages own pending, success, empty, and failure states; presentation
components do not call `fetch` directly. Mutation retries preserve their parking-session idempotency key or
reservation identifier until a command succeeds.

The allocation application interface exposes point-in-time occupancy totals backed by PostgreSQL. The reporting HTTP
adapter publishes facility and per-floor counts without reading another module's tables directly. A shared server-sent
event broadcaster polls once per connected facility, emits only changed snapshots, and sends heartbeats between changes.
The dashboard preserves the last successful response, falls back to 15-second polling while disconnected, and exposes a
manual refresh control.

The Vite development server proxies same-origin API paths to the backend. A separately hosted deployment must explicitly
configure the API base URL and cross-origin policy. Live updates will use server-sent events unless bidirectional
communication becomes necessary.

## Security

The API and dashboard currently have no authentication or authorization. They must remain in a trusted development
environment until the identity milestone implements and verifies those boundaries.

Personal data is limited to what is required to identify a vehicle and parking session. Retention rules will be
approved before production readiness.

## Operations

The application currently provides startup, readiness, and liveness health endpoints and graceful shutdown. Planned
operational work includes structured logs, correlation identifiers, allocation and database metrics, and trace
propagation.

Local development uses PostgreSQL, Maven, Node.js, and Vite. Production packaging and deployment topology remain
undecided.

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
- [Reservation domain](reservation-domain.md)
- [Pricing domain](pricing-domain.md)
- [Occupancy reporting](occupancy-reporting.md)
- [API guide](../api/README.md)
- [Frontend component standards](../frontend/component-standards.md)
- [Persistence](persistence.md)
