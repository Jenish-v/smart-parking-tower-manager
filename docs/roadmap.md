# Delivery Roadmap

Work is delivered as reviewable vertical slices. A milestone is complete only when its implementation, tests, and
maintained documentation agree.

Milestones 1 through 10 are implemented. Milestone 11 is in progress: versioned rate plans and deterministic fee
calculation are implemented, while persistence, adjustments, and parking-session receipts remain.

The completed baseline is integrated into `main`. The maintained local runtime builds PostgreSQL, the backend, and the
operator dashboard through Docker Compose. Work continues by milestone until the release-candidate acceptance criteria
are satisfied; the roadmap has no calendar-based completion claim.

## Milestone 1: Backend baseline

- Create the Java 21 and Spring Boot application.
- Add build, static analysis, and test tasks.
- Add health endpoints and baseline configuration.
- Run backend checks in GitHub Actions.

## Milestone 2: Facility domain

- Model facilities, floors, zones, spaces, and size classes.
- Define compatibility and operational-state rules.
- Protect domain invariants with unit tests.
- Document module ownership.

## Milestone 3: Persistence baseline

- Add PostgreSQL and Flyway.
- Create the initial facility schema.
- Add the 7,200-space reference fixture.
- Verify migrations with Testcontainers.

## Milestone 4: Allocation domain

- Implement deterministic candidate ordering.
- Add park, find, unpark, and availability use cases.
- Enforce vehicle and space uniqueness in the domain.
- Cover allocation boundaries with unit tests.

## Milestone 5: Transactional allocation

- Persist parking allocation atomically.
- Implement and document the database locking strategy.
- Add concurrent allocation and retry tests.
- Prevent duplicate active assignments at the database boundary.

## Milestone 6: Parking sessions

- Record entry, active stay, and exit.
- Add request idempotency.
- Define session history and retention behaviour.
- Cover invalid state transitions.

## Milestone 7: Public API

- Add versioned REST endpoints.
- Publish the OpenAPI contract and error format.
- Add request validation and API integration tests.
- Provide working request examples.

## Milestone 8: Operator dashboard baseline

- Create the React and TypeScript application.
- Establish routing, API access, and component standards.
- Add the main operator layout.
- Run frontend checks in GitHub Actions.

## Milestone 9: Live parking operations

- Add occupancy, vehicle search, entry, and exit workflows.
- Integrate live occupancy updates.
- Handle stale data and failed mutations.
- Add accessibility and component tests.

## Milestone 10: Reservations

- Add reservation lifecycle and arrival matching.
- Protect capacity from conflicting claims.
- Add cancellation and expiry behaviour.
- Test time-bound reservation rules.

## Milestone 11: Pricing

- Add versioned rate plans and fee calculation. Implemented.
- Record manual adjustments with reasons.
- Add parking-session receipts.
- Test time, rounding, and pricing boundaries.

## Milestone 12: Identity and audit

- Integrate OpenID Connect.
- Add operator and administrator roles.
- Record sensitive actions in append-only audit history.
- Test authentication and authorization boundaries.

## Milestone 13: Operations and hardening

- Add metrics, tracing, and structured logs.
- Add backup, restore, deployment, and rollback guidance.
- Run load, recovery, and graceful-shutdown tests.
- Complete security and dependency review.

## Milestone 14: Release candidate

- Run the complete acceptance suite.
- Resolve release-blocking defects and documentation gaps.
- Publish the deployment and operator runbooks.
- Publish the first versioned release and changelog.
