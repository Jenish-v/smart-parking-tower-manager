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

This slice defines lifecycle invariants only. It does not persist reservations, claim capacity, run expiry jobs, or
coordinate a matched arrival with parking-session entry. Those behaviours remain application and persistence work in
Milestone 10.
