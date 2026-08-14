# ADR-0002: Use PostgreSQL row locks for allocation

Date: 2026-08-14

Status: Accepted

## Context

Allocation must select the first compatible free space without assigning one vehicle twice or assigning one space to
two vehicles. Multiple application threads or instances can handle entry requests concurrently. Process-local locks
would not coordinate those instances, and an application-only availability check would race with insertion.

Serializing every entry request through one global lock would preserve correctness but reduce throughput and introduce
a coordination point outside the system of record.

## Decision

Run candidate selection and assignment insertion in one PostgreSQL transaction.

Candidate selection orders active compatible spaces by floor number, zone code, and space number, then uses
`FOR UPDATE OF parking_spaces SKIP LOCKED`. A request skips space rows already being evaluated by another transaction
and selects the next eligible row. Partial unique indexes independently enforce one active assignment per vehicle
within a facility and one active assignment per space.

Retry the complete transaction at most three times for Spring transient data-access failures. Do not retry capacity,
duplicate-vehicle, validation, or other domain failures.

Unpark locks the active allocation row before recording its release time.

## Consequences

Correctness is coordinated by the same PostgreSQL database that stores allocation state. Requests for different spaces
can proceed concurrently, and a locked candidate does not create a lock queue.

Under contention, a request can select a later compatible space while an earlier one is locked. Deterministic ordering
therefore applies to the eligible, currently unlocked set. A request can report no capacity when every compatible free
row is temporarily locked; the caller can issue a later request, while automatic retries remain limited to database
errors.

The strategy depends on PostgreSQL locking semantics and is covered by PostgreSQL integration tests. A different
database would require a reviewed replacement strategy.
