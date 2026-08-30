# Reservation Domain

The reservation module owns future parking claims. Its domain depends on facility identity and size classes plus the
normalized vehicle identifier already shared by allocation and parking sessions. It does not access either module's
persistence model.

A reservation records its facility, vehicle, required size, creation time, and arrival window. Windows are half-open:
the start instant is eligible and the end instant is not. Creation may occur at the start instant but not after it.

Confirmed reservations have three terminal transitions:

| Transition | Time rule | Result |
| --- | --- | --- |
| Cancel | At or after creation and before the window ends | `CANCELLED` |
| Fulfill | Inside the arrival window | `FULFILLED` |
| Expire | At or after the window ends | `EXPIRED` |

Arrival matching also requires the facility and normalized vehicle identifier to match. Terminal reservations never
match another arrival and cannot transition again.

PostgreSQL persists reservations and serializes facility-scoped capacity decisions with a transaction advisory lock.
The capacity check treats compatible spaces as nested pools: large claims consume large-only capacity, medium claims
consume medium-or-large capacity, and all claims consume total compatible capacity. A sweep over existing window
boundaries rejects any candidate that would exceed one of those pools.

Creation uses a client-selected reservation UUID. Repeating an identical request returns the stored reservation;
reusing the UUID for different facts returns a conflict. Cancellation is also repeat-safe. Read operations transition
confirmed reservations whose windows have ended to `EXPIRED`; a scheduled expiry job is not yet implemented.

The module does not reserve a physical space. Coordinating a matched arrival with parking-session entry and exposing
reservation controls in the operator dashboard remain Milestone 10 work.
