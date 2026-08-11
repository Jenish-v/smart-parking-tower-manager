# Delivery Roadmap

Work is delivered as reviewable vertical slices. A milestone is complete only when its implementation, tests, and maintained documentation agree.

## Milestone 0: Repository foundation

- Define scope and system boundaries.
- Establish contribution, security, and documentation standards.
- Record the initial architecture decision.
- Publish the staged delivery plan.

## Milestone 1: Backend baseline

- Create the Java 21 and Spring Boot application.
- Add build, formatting, static analysis, and test tasks.
- Add health endpoints and baseline configuration.
- Run backend checks in GitHub Actions.

## Milestone 2: Facility model

- Model facilities, floors, zones, spaces, and size classes.
- Add PostgreSQL schema migrations.
- Add the 7,200-space reference fixture.
- Test capacity and compatibility rules.

## Milestone 3: Allocation engine

- Implement deterministic candidate ordering.
- Add park, find, unpark, and availability use cases.
- Enforce vehicle and space uniqueness.
- Add unit and database integration tests.

## Milestone 4: Parking sessions

- Record entry, active stay, and exit.
- Add request idempotency.
- Define session history and retention behaviour.
- Cover invalid state transitions.

## Milestone 5: Public API

- Add versioned REST endpoints.
- Publish the OpenAPI contract and error format.
- Add request validation and API integration tests.
- Provide working request examples.

## Milestone 6: Concurrency and recovery

- Implement and document the database locking strategy.
- Run concurrent allocation and retry tests.
- Handle transaction conflicts and transient database failures.
- Add a repeatable load-test profile.

## Milestone 7: Operator dashboard

- Create the React and TypeScript application.
- Add occupancy, vehicle search, entry, and exit workflows.
- Add accessibility and component tests.
- Integrate live occupancy updates.

## Milestone 8: Reservations and pricing

- Add reservation lifecycle and arrival matching.
- Add versioned rate plans and fee calculation.
- Record manual adjustments with reasons.
- Test time, rounding, and cancellation boundaries.

## Milestone 9: Identity and audit

- Integrate OpenID Connect.
- Add operator and administrator roles.
- Record sensitive actions in an append-only audit history.
- Test authorization boundaries.

## Milestone 10: Operations

- Add metrics, tracing, structured logs, and dashboards.
- Add backup and restore guidance.
- Document deployment, rollback, and incident procedures.
- Test graceful shutdown and readiness behaviour.

## Milestone 11: Release candidate

- Complete security and dependency review.
- Run acceptance, load, and recovery tests.
- Resolve documentation gaps.
- Publish the first versioned release and changelog.
