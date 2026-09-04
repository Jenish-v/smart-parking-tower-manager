# Persistence

PostgreSQL is the system of record. Facility, allocation, parking-session, reservation, and pricing schemas are managed only
through Flyway migrations.

## Schema ownership

| Module | Tables |
| --- | --- |
| Facility | `facilities`, `parking_floors`, `parking_zones`, `parking_spaces` |
| Allocation | `active_allocations` |
| Parking sessions | `parking_sessions`, `parking_session_requests` |
| Reservations | `reservations` |
| Pricing | `pricing_rate_plans`, `pricing_rate_bands`, `parking_receipts`, `fee_adjustments` |

The facility hierarchy stores configured space identity, size, and operational state. Natural identifiers are unique
within their parent, and check constraints reproduce domain identifier and state rules.

Allocation rows record vehicle-to-space assignment and release facts. Partial unique indexes prevent more than one
active assignment for a vehicle within a facility or for a parking space.

Parking-session rows record entry, active stay, and exit. They retain the allocated location and optional fulfilled
reservation identifier as lifecycle facts. Partial indexes prevent duplicate active vehicles and spaces, while a unique
index prevents one reservation from being linked to two sessions. Request rows bind idempotency identifiers to
operations and sessions.

Reservation rows record the vehicle, minimum size, half-open arrival window, lifecycle status, and terminal transition
time. Indexes support confirmed-window capacity checks and vehicle history. Constraints reproduce the domain's size,
status, identifier, window, and transition-time rules.

Pricing rows store immutable versioned plans and a complete rate band for each size. An exclusion constraint prevents
overlapping effective windows. Each completed session has at most one receipt snapshot containing its exact plan
version, calculation inputs, gross charge, cap discount, total, and currency.
Fee-adjustment rows retain signed minor-unit amounts, controlled reason codes, free-text detail, an operator reference,
and creation time. A transaction-scoped advisory lock serializes reuse of an adjustment identifier, then the receipt
row is locked before an adjustment is appended. Primary-key replay detection and an exact fact comparison make retries
safe, while the transaction rejects any adjusted total below zero.

## Transactions and locking

The allocation adapter selects a compatible `parking_spaces` row with
`FOR UPDATE OF parking_spaces SKIP LOCKED`. Selection, insertion, and release execute within database transactions.
The fixed floor, zone, and space ordering preserves deterministic selection among rows that are not already locked.
Bounded retries rerun a complete allocation transaction after transient data-access failures.

Parking-session entry and exit wrap calls to the allocation application interface in an outer transaction. Standard
Spring transaction propagation makes session, allocation, and matched-reservation writes commit or roll back together.
Receipt assessment participates in the exit transaction, so missing pricing or a failed receipt write also restores the
active session and allocation.
PostgreSQL advisory transaction locks serialize reuse of one request identifier without creating process-local
coordination. A matched reservation is row-locked before its fulfillment transition.

Reservation creation takes a facility-scoped advisory transaction lock before reading capacity and inserting the
claim. This serializes competing decisions for one facility. The service checks peak overlap in the total,
medium-or-large, and large-only compatible pools. Reservation UUIDs provide durable replay detection, and cancellation
locks the selected row before applying a transition.

See [ADR-0002](../decisions/0002-use-postgresql-row-locks-for-allocation.md) for the allocation locking decision.

## Migrations

Production schema migrations are under `backend/src/main/resources/db/migration`. They are immutable after release.

Development fixtures are under `backend/src/main/resources/db/devdata` and load only with the Spring `local` profile.
The reference fixture uses deterministic UUIDs and creates 7,200 active spaces across six floors and six zones per
floor. Every zone has 100 small, 80 medium, and 20 large spaces. A separate development fixture provides the labelled
CAD reference rate used by local exit workflows.

Testcontainers applies both locations to PostgreSQL and verifies the fixture, constraints, deterministic selection,
allocation concurrency, session transitions, request replay, rollback behaviour, reservation capacity concurrency,
cancellation, expiry, arrival fulfillment, pricing persistence, receipt replay, adjustment replay and conflict, and
history.
