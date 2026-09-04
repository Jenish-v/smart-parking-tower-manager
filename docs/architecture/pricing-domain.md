# Pricing Domain

## Scope

The pricing domain calculates a parking fee from an immutable rate-plan version, vehicle size, entry time, and exit
time. It has no dependency on Spring, persistence, or HTTP so the same rules can be exercised before a receipt is
stored or presented through an API.

The local development fixture ships with a labelled CAD reference plan so the complete stack can exercise paid exits.
It is not a production price recommendation. Non-local environments must provision an applicable plan as an operating
decision before processing exits.

## Rate plans

A rate plan has a stable identifier and an explicit positive version. Its effective interval is half open: the start
instant is included and the optional end instant is excluded. Each version defines:

- one grace period;
- one billing increment;
- a charge and rolling 24-hour cap for every vehicle size class;
- one currency shared by all size classes.

Historical versions must remain immutable after use. A new price or policy therefore creates a new version instead of
editing the values that produced an existing receipt.

PostgreSQL prevents overlapping effective windows, so exactly one plan can apply at a session's entry time. Exits fail
without releasing the allocation when no plan applies.

## Calculation

The calculation removes the grace period from the stay, then rounds each remaining duration up to the next billing
increment. It divides multi-day stays into rolling 24-hour periods and applies the configured cap independently to each
period. A partial final period has the same cap.

Money is represented in currency minor units. The calculation does not use floating-point arithmetic, and arithmetic
overflow fails explicitly. The resulting quote records gross charge, cap discount, total, billable duration, increment
count, and the exact rate-plan identifier and version.

## Receipts and adjustments

Rate plans and receipt snapshots are persisted. Parking-session exit selects the version effective at entry, calculates
the fee, and stores one receipt in the same transaction as allocation release and session completion. Replaying the exit
returns the same receipt.

Vehicle history includes the immutable base receipt for a completed session. A separate receipt statement contains the
base total, signed adjustments, and current adjusted total. Adjustments are append-only and require a client-selected
UUID, signed minor-unit amount, reason code, reason detail, operator reference, and creation time. Replaying identical
facts under the same UUID returns the existing statement; changing any fact returns a conflict. An adjustment cannot
reduce the statement below zero.

The base receipt remains unchanged so its rate-plan calculation can always be reproduced. An operator reference is an
unverified caller-supplied label until identity is implemented. Rate-plan administration APIs, tax handling, and
refund settlement are not implemented. Payment processing is outside the current roadmap; receipts and adjustments
record assessed fees and corrections but do not claim payment or refund settlement.
