# Public API

The backend exposes parking-session operations under `/api/v1`. The maintained OpenAPI 3.0 contract is
`backend/src/main/resources/static/openapi.yaml` and is served at `GET /openapi.yaml`.

## Commands

Entry starts a session and assigns the first compatible space:

```bash
curl -i http://localhost:8080/api/v1/facilities/d936bb7d-3027-47aa-a47b-d04a37e07310/parking-sessions/entries \
  -X POST \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 3d30408d-7c3a-4f61-9138-3b9b728782da' \
  -d '{"vehicleIdentifier":"TOR 501","requiredSize":"SMALL"}'
```

Exit completes the active session and releases its allocation:

```bash
curl -i http://localhost:8080/api/v1/facilities/d936bb7d-3027-47aa-a47b-d04a37e07310/parking-sessions/exits \
  -X POST \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: f550403c-a3b6-40ce-a158-f867c48ca80f' \
  -d '{"vehicleIdentifier":"TOR 501"}'
```

Use a new UUID for each logical command. Retry the same command with the same identifier after a timeout or transient
failure. Reusing an identifier with a different operation, facility, vehicle, or entry size returns a conflict.

Reservation creation puts the client-selected reservation UUID in the path:

```bash
curl -i http://localhost:8080/api/v1/facilities/d936bb7d-3027-47aa-a47b-d04a37e07310/reservations/8ab2e819-f05b-41d7-b54d-32e59c9f035d \
  -X PUT \
  -H 'Content-Type: application/json' \
  -d '{"vehicleIdentifier":"TOR 501","requiredSize":"SMALL","startsAt":"2026-09-01T14:00:00Z","endsAt":"2026-09-01T15:00:00Z"}'
```

Retrying the same UUID and body returns the stored reservation. Reusing the UUID for different reservation facts
returns a conflict. Cancellation is repeat-safe:

```bash
curl -i http://localhost:8080/api/v1/facilities/d936bb7d-3027-47aa-a47b-d04a37e07310/reservations/8ab2e819-f05b-41d7-b54d-32e59c9f035d \
  -X DELETE
```

## Queries

Active lookup returns one session or a not-found problem:

```bash
curl 'http://localhost:8080/api/v1/facilities/d936bb7d-3027-47aa-a47b-d04a37e07310/parking-sessions/active?vehicleIdentifier=TOR%20501'
```

History returns an array ordered by newest entry first:

```bash
curl 'http://localhost:8080/api/v1/facilities/d936bb7d-3027-47aa-a47b-d04a37e07310/parking-sessions?vehicleIdentifier=TOR%20501'
```

Reservation lookup uses its UUID. Reservation history is ordered by newest creation first:

```bash
curl 'http://localhost:8080/api/v1/facilities/d936bb7d-3027-47aa-a47b-d04a37e07310/reservations/8ab2e819-f05b-41d7-b54d-32e59c9f035d'
curl 'http://localhost:8080/api/v1/facilities/d936bb7d-3027-47aa-a47b-d04a37e07310/reservations?vehicleIdentifier=TOR%20501'
```

Vehicle identifiers are normalized to uppercase with repeated whitespace collapsed. Required sizes are `SMALL`,
`MEDIUM`, or `LARGE`.

Occupancy returns current facility totals and a floor-by-floor breakdown:

```bash
curl 'http://localhost:8080/api/v1/facilities/d936bb7d-3027-47aa-a47b-d04a37e07310/occupancy'
```

The response distinguishes total capacity, operational capacity, active occupancy, and currently available spaces. Its
`capturedAt` value identifies when the point-in-time snapshot was read.

The stream sends an initial named `occupancy` event, then sends another when counts change. Keepalive comments maintain
idle connections. Browsers reconnect automatically after a stream interruption:

```bash
curl -N 'http://localhost:8080/api/v1/facilities/d936bb7d-3027-47aa-a47b-d04a37e07310/occupancy/stream'
```

## Errors

Errors use `application/problem+json`. Every problem includes the RFC problem fields plus:

- `code`: stable machine-readable identifier
- `timestamp`: UTC failure time
- `violations`: field validation details when applicable

Current codes are `VALIDATION_FAILED`, `FACILITY_NOT_FOUND`, `INVALID_REQUEST`, `ACTIVE_SESSION_EXISTS`,
`IDEMPOTENCY_CONFLICT`, `NO_COMPATIBLE_SPACE`, `ACTIVE_SESSION_NOT_FOUND`, `RESERVATION_CAPACITY_EXCEEDED`,
`OVERLAPPING_VEHICLE_RESERVATION`, `RESERVATION_IDENTIFIER_CONFLICT`, `INVALID_RESERVATION_STATE`,
`RESERVATION_NOT_FOUND`, `DATABASE_UNAVAILABLE`, `DATABASE_ERROR`, and `INTERNAL_ERROR`.

## Security boundary

Authentication and authorization are not implemented. Run the API only in a trusted development environment until the
identity milestone adds and verifies those controls.
