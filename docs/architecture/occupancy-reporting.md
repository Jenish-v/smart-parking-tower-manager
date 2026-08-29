# Occupancy Reporting

The allocation module owns the current occupied-space state and exposes a read-only `OccupancyService` application
interface. Its PostgreSQL adapter combines active allocations with the facility hierarchy in one consistent query. The
reporting HTTP adapter consumes that interface and does not access allocation tables directly.

`GET /api/v1/facilities/{facilityId}/occupancy` returns a point-in-time snapshot with total, operational, occupied, and
available space counts for the facility and each floor. Total capacity includes out-of-service spaces. Operational
capacity excludes them, and availability is operational capacity minus active allocations.

`GET /api/v1/facilities/{facilityId}/occupancy/stream` sends an initial `occupancy` event and emits another event when
facility or floor counts change. The reporting adapter shares one five-second database refresh per connected facility,
rather than polling once per browser, and sends keepalive comments when counts are unchanged. It removes disconnected
subscribers and closes each facility's subscribers after a refresh failure so clients can reconnect cleanly.

Clients treat `capturedAt` as the freshness boundary. The dashboard preserves the last successful response, relies on
the browser's event-stream reconnection, and falls back to 15-second snapshot polling while the stream is disconnected.
