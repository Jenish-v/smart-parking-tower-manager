# Allocation Domain

The allocation module owns active vehicle-to-space assignments and deterministic candidate selection. Domain types are
under `com.jenish.smartparking.allocation.domain`; callers use the
`com.jenish.smartparking.allocation.application.AllocationService` interface.

## Selection order

A persisted parking request locks and selects candidates by:

1. floor number, ascending
2. zone code, ascending
3. space number, ascending

A candidate must be active, unoccupied, and equal to or larger than the vehicle's required size. An occupied space
returns to the candidate set only after its active assignment is released.

## Invariants

The database permits at most one active assignment for a vehicle within a facility and one active assignment for a
space. Partial unique indexes enforce both rules independently of application instances. Released rows are retained as
allocation history.

The `AllocationManager` remains a framework-free expression of deterministic selection and availability rules.
`JdbcAllocationService` implements transactional park, find, and unpark operations without exposing persistence types
through the application boundary.

## Concurrency

A park transaction checks the vehicle's active assignment, then selects the first compatible space using
`FOR UPDATE OF parking_spaces SKIP LOCKED`. Concurrent requests do not wait on a candidate already being assigned;
they continue through the deterministic candidate order. The insert and row lock share one transaction.

A concurrent request for the same vehicle can lock a different space before either insert is visible. The partial
vehicle index resolves that race, the losing transaction rolls back, and the application reports that the vehicle is
already parked. Transient data-access failures retry the complete transaction at most three times.

Unpark locks the active assignment before recording its release time. Find is read-only and returns the same domain
record used by the in-memory manager.

## Current boundary

The parking-session module coordinates entry and exit through the allocation application interface; it does not access
allocation tables. Availability remains on the in-memory domain manager. Reservations and the public API belong to later
milestones. Allocation history records assignment facts, while parking-session history owns the operator-facing vehicle
lifecycle.
