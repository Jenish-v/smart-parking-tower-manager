# Pricing Domain

## Scope

The pricing domain calculates a parking fee from an immutable rate-plan version, vehicle size, entry time, and exit
time. It has no dependency on Spring, persistence, or HTTP so the same rules can be exercised before a receipt is
stored or presented through an API.

No active rate plan ships with the application. Currency, prices, grace periods, effective windows, and billing
increments are operating decisions and must be configured through the persisted rate-plan work before fees can be
charged by a parking session.

## Rate plans

A rate plan has a stable identifier and an explicit positive version. Its effective interval is half open: the start
instant is included and the optional end instant is excluded. Each version defines:

- one grace period;
- one billing increment;
- a charge and rolling 24-hour cap for every vehicle size class;
- one currency shared by all size classes.

Historical versions must remain immutable after use. A new price or policy therefore creates a new version instead of
editing the values that produced an existing receipt.

## Calculation

The calculation removes the grace period from the stay, then rounds each remaining duration up to the next billing
increment. It divides multi-day stays into rolling 24-hour periods and applies the configured cap independently to each
period. A partial final period has the same cap.

Money is represented in currency minor units. The calculation does not use floating-point arithmetic, and arithmetic
overflow fails explicitly. The resulting quote records gross charge, cap discount, total, billable duration, increment
count, and the exact rate-plan identifier and version.

## Boundaries

The current module calculates deterministic quotes only. Active-plan selection, database storage, manual adjustments,
session receipts, payment collection, tax handling, refunds, API endpoints, and dashboard workflows are not yet
implemented. Payment processing is outside the current roadmap; receipts will record assessed parking fees rather than
claiming settlement.
