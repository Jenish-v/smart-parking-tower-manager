# Occupancy Reporting

The allocation module owns the current occupied-space state and exposes a read-only `OccupancyService` application
interface. Its PostgreSQL adapter combines active allocations with the facility hierarchy in one consistent query. The
reporting HTTP adapter consumes that interface and does not access allocation tables directly.

`GET /api/v1/facilities/{facilityId}/occupancy` returns a point-in-time snapshot with total, operational, occupied, and
available space counts for the facility and each floor. Total capacity includes out-of-service spaces. Operational
capacity excludes them, and availability is operational capacity minus active allocations.

The snapshot is not an event stream. Clients must treat `capturedAt` as the freshness boundary. Server-sent updates and
the dashboard refresh strategy remain part of the next live-occupancy slice.
