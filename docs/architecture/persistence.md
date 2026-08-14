# Persistence

PostgreSQL is the system of record. Facility and allocation schemas are managed only through Flyway migrations.

## Schema ownership

The facility module owns four tables:

| Table | Purpose |
| --- | --- |
| `facilities` | Facility identity and name |
| `parking_floors` | Ordered floors within a facility |
| `parking_zones` | Named zones within a floor |
| `parking_spaces` | Numbered, sized spaces and operational state |

The allocation module owns `active_allocations`. Each row records a vehicle, required size, selected space, allocation
time, and optional release time. Partial unique indexes prevent more than one active row for a vehicle within a facility
or for a parking space. History indexes support lookup without weakening the active-assignment constraints.

Natural facility identifiers are unique within their parent: floor number within a facility, zone code within a floor,
and space number within a zone. Check constraints reproduce domain identifier, size-class, operational-state, and
allocation timestamp rules at the database boundary.

## Transactions and locking

The allocation adapter selects a compatible `parking_spaces` row with
`FOR UPDATE OF parking_spaces SKIP LOCKED`. Selection, insertion, and release execute within database transactions.
The fixed floor, zone, and space ordering preserves deterministic selection among rows that are not already locked.
Bounded retries rerun a complete transaction after transient data-access failures.

See [ADR-0002](../decisions/0002-use-postgresql-row-locks-for-allocation.md) for the decision and trade-offs.

## Migrations

Production schema migrations are under `backend/src/main/resources/db/migration`. They are immutable after release.

Development fixtures are under `backend/src/main/resources/db/devdata` and load only with the Spring `local` profile.
The reference fixture uses deterministic UUIDs and creates 7,200 active spaces across six floors and six zones per
floor. Every zone has 100 small, 80 medium, and 20 large spaces.

Testcontainers applies both locations to PostgreSQL and verifies the fixture, constraints, deterministic selection,
release history, concurrent vehicle claims, concurrent space claims, and transaction retries.
