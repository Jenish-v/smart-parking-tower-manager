# Operator Runbook

This runbook covers normal parking operations. It assumes the backend is ready, the dashboard targets the correct
facility, and the operator has an `OPERATOR` or `ADMIN` identity in secured environments.

## Start of shift

1. Confirm the dashboard shows the expected facility and a current occupancy timestamp.
2. Confirm the facility total is 7,200 spaces for the maintained reference deployment.
3. Check readiness at `/actuator/health/readiness` if the dashboard reports stale data.
4. Escalate a failed readiness check before admitting vehicles. Do not edit PostgreSQL records directly.

## Vehicle entry

Search for the vehicle before starting entry. An existing active session must be resolved rather than duplicated. Enter
the normalized registration and required vehicle size. The assigned floor, zone, and space are authoritative.

A confirmed reservation inside its arrival window is fulfilled automatically. A size mismatch is rejected and must be
corrected against the reservation; it must not be bypassed as a walk-in. If a retry is needed after a timeout, retain
the same request identifier so the backend returns the original result.

## Vehicle exit

Search for the active session, confirm the assigned vehicle, then submit exit once. The completed result includes the
immutable base receipt. Retry a timed-out exit with the same request identifier. Never create a second logical exit to
force a response.

If the service reports `RATE_PLAN_UNAVAILABLE`, the session and allocation remain active. Escalate the missing rate-plan
configuration before retrying. Do not release the space manually.

## Reservations

Create reservations with the customer vehicle identifier, required size, and an explicit arrival window. The service
rejects overlapping reservations for the same vehicle and reservations that exceed compatible capacity. Cancellation
is allowed only while a reservation is confirmed. Fulfilled, expired, and cancelled reservations are immutable terminal
records.

## Receipts, adjustments, and audit history

The dashboard displays receipt totals in completed vehicle history. Full receipt statements, administrator adjustments,
and audit history are available through the documented API. Adjustments require `ADMIN`, a signed amount, a reason code,
and a concise factual detail. They append to the statement; they do not replace the original charge.

Use `CUSTOMER_SERVICE`, `RATE_CORRECTION`, `OPERATIONAL_EXCEPTION`, or `OTHER` consistently. Never include payment-card
data, access tokens, or unnecessary personal information in the detail. Each successful adjustment records the verified
actor and an immutable audit event.

## Degraded operation

- Stale occupancy with a ready backend: refresh once, then check the polling and event-stream errors in browser tools.
- Backend not ready: stop new mutations and follow the deployment or incident procedure.
- Database unavailable: preserve request identifiers and retry only after readiness returns.
- Unknown or conflicting command result: search the vehicle and reservation history before retrying.
- Capacity rejection: do not override the database. Confirm facility, size, operational state, and reservation window.

Record the request identifier, facility, UTC time, stable problem code, and correlation header when escalating. Exclude
tokens and credentials. Use the structured backend log and trace for that correlation identifier.

## End of shift

Confirm unresolved active-session exceptions and failed reservations have an owner. Administrators should review
unexpected adjustment audit events. Operational data is retained until the approved environment-specific retention
policy is implemented; operators must not perform ad hoc deletion.
