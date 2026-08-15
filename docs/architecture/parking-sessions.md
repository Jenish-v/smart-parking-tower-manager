# Parking Sessions

The parking-session module owns the vehicle lifecycle from entry through active stay to exit. Domain types are under
`com.jenish.smartparking.parkingsession.domain`; callers use
`com.jenish.smartparking.parkingsession.application.ParkingSessionService`.

## Lifecycle

Entry requests contain a request identifier, facility, vehicle identifier, and required size. The module calls the
allocation application interface, then records the selected location and entry time in the same transaction.

An active session can transition to completed exactly once. Exit releases the allocation and records the exit time in
one transaction. Repeated completion, exit without an active session, and a second active entry for the same vehicle
are rejected.

The application interface supports active-session lookup and complete vehicle history ordered by newest entry first.

## Idempotency

Every entry and exit command requires a UUID request identifier. PostgreSQL transaction-scoped advisory locks serialize
commands carrying the same identifier. The first command stores its operation, target vehicle, and session. A matching
retry returns the same session identity without repeating allocation or release.

Reusing an identifier for another operation, facility, vehicle, or entry size is an idempotency conflict. Request
records are stored with their session so replay remains available after application restarts.

A replay returns the session's current state. An entry replay after a later successful exit therefore returns the same
session identity in its completed state.

## Ownership and transactions

The parking-session module owns `parking_sessions` and `parking_session_requests`. It does not query or update the
allocation module's tables. Coordination occurs through `AllocationService`, with Spring transaction propagation
joining both module operations to one PostgreSQL transaction.

Database partial indexes independently prevent two active sessions for one vehicle or one recorded space. These
constraints supplement allocation constraints rather than replacing them.

## History and retention

Completed sessions are retained as history and are not modified after exit. Request records are retained for
idempotency. The current application has no automated deletion or anonymization process.

A production retention duration has not been approved. Vehicle identifiers are personal operational data, so deployment
requires a reviewed retention and deletion policy before any purge job is introduced.
