# Persistence Baseline

PostgreSQL is the system of record. The initial schema belongs to the facility module and is managed only through
Flyway migrations.

## Schema ownership

The facility module owns four tables:

| Table | Purpose |
| --- | --- |
| `facilities` | Facility identity and name |
| `parking_floors` | Ordered floors within a facility |
| `parking_zones` | Named zones within a floor |
| `parking_spaces` | Numbered, sized spaces and operational state |

Foreign keys use cascading deletes because floors, zones, and spaces have no independent lifecycle outside their
configured facility. Application use cases must still authorize and coordinate facility removal before issuing a
delete.

Natural identifiers are unique within their parent: floor number within a facility, zone code within a floor, and
space number within a zone. Check constraints reproduce the domain's identifier, size-class, and operational-state
rules at the database boundary.

## Migrations

Production schema migrations are under `backend/src/main/resources/db/migration`. They are immutable after release.

Development fixtures are under `backend/src/main/resources/db/devdata` and load only with the Spring `local` profile.
The reference fixture uses deterministic UUIDs and creates 7,200 active spaces across six floors and six zones per
floor. Every zone has 100 small, 80 medium, and 20 large spaces.

Testcontainers runs both locations against PostgreSQL during integration verification. Allocation state, parking
sessions, reservations, and pricing tables remain outside this milestone and will be introduced by their owning
modules.
