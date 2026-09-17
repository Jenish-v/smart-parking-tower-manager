# Changelog

Notable changes are recorded in this file. Versions follow Semantic Versioning while the project remains pre-1.0.

## [0.1.0-rc.1] - 2026-09-16

### Added

- Deterministic allocation across the 7,200-space reference facility with transactional PostgreSQL locking.
- Idempotent parking entry and exit, vehicle history, occupancy reporting, and server-sent occupancy updates.
- Capacity-safe reservations with cancellation, expiry, and atomic arrival fulfillment.
- Versioned rate plans, immutable receipts, reason-coded adjustments, and append-only audit history.
- OIDC authentication, operator and administrator roles, and dashboard Authorization Code flow with PKCE.
- Responsive React operator workflows for occupancy, parking sessions, reservations, and receipt totals.
- Prometheus metrics, ECS structured logs, request correlation, OpenTelemetry tracing, and local Jaeger inspection.
- Docker Compose runtime, database recovery, graceful-shutdown, k6 load, security, and integrated acceptance checks.

### Security

- Added CodeQL and Trivy enforcement, weekly dependency updates, unprivileged read-only containers, loopback-only local
  services, and dashboard security headers.

### Release boundaries

- This release candidate is validated for the maintained local Docker Compose runtime.
- Internet-facing deployment still requires the environment-specific identity, ingress, secret, retention, backup,
  capacity, and incident-response controls documented in the security and deployment guides.
- The included facility and CAD rate plan are deterministic reference data, not an operating configuration.

[0.1.0-rc.1]: https://github.com/Jenish-v/smart-parking-tower-manager/releases/tag/v0.1.0-rc.1
