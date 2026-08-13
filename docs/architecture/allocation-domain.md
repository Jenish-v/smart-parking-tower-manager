# Allocation Domain

The allocation module owns active vehicle-to-space assignments and deterministic candidate selection. Its domain
package is `com.jenish.smartparking.allocation.domain`.

## Selection order

The manager reads immutable facility configuration and orders candidates by:

1. floor number, ascending
2. zone code, ascending
3. space number, ascending

A candidate must be active, unoccupied, and equal to or larger than the vehicle's required size. An occupied space is
never returned to the candidate set until its vehicle is unparked.

## Invariants

The module maintains two indexes: vehicle to allocation and space to vehicle. A park operation fails if the vehicle
already has an allocation or no compatible space remains. Unpark removes both index entries. Find returns the current
allocation without exposing either mutable index.

Availability reports operational and occupied totals, free spaces by physical size, and compatible capacity for each
vehicle size. Out-of-service spaces are excluded from every availability count.

## Current boundary

`AllocationManager` holds state in one process and is intended to express and test the domain rules. It is not a
concurrency or recovery boundary. It does not persist assignments, coordinate database locks, create parking-session
history, or provide request idempotency.

The transactional allocation milestone will implement a persistence adapter and database constraints without moving
selection rules into the web or persistence layers. Until that work is complete, the allocation domain is not exposed
through the public API.
