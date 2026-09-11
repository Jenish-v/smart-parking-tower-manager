# Deployment and Rollback

This runbook defines the application-level deployment contract. The repository does not prescribe a cloud provider or
orchestrator. A target platform must satisfy these controls before it is approved for internet-facing traffic.

## Release artifact

Build backend and frontend images once in continuous integration. Identify each immutable image by digest and promote
the same digests between environments. Do not rebuild source for production or deploy a mutable tag as the recorded
release artifact.

The backend container runs as the unprivileged `smartparking` user. Terminate TLS at an approved ingress or load
balancer and keep PostgreSQL and telemetry ingestion on private networks. Supply database credentials, the OIDC issuer,
and telemetry credentials through the platform secret store. Do not place secrets in image layers, deployment files,
logs, or command history.

## Health and traffic

Configure separate probes:

- `/actuator/health/liveness` determines whether the process must restart.
- `/actuator/health/readiness` determines whether the instance receives traffic.

Both endpoints are public and omit component details. All other Actuator endpoints retain their authorization rules.
The platform must remove an instance from routing when readiness fails and must not use liveness to detect temporary
database unavailability.

## Rolling deployment

Deploy at least one new backend instance before terminating an old instance. Wait for readiness, then remove the old
instance from routing and send `SIGTERM`. Allow at least 30 seconds before `SIGKILL`; Spring has a 20-second graceful
shutdown phase. The maintained Compose runtime encodes this margin with `stop_grace_period`.

Database migrations run during backend startup. Every migration in one release must remain compatible with the
immediately previous application release so old and new instances can overlap during rollout. Use additive schema
changes first. Delay destructive schema removal until no supported application version depends on it.

After rollout, verify:

- backend and dashboard readiness
- database connection-pool health and migration version
- error rate, request latency, and saturation against the pre-deployment baseline
- one controlled entry, lookup, exit, reservation, and receipt workflow
- logs and traces for the controlled request identifiers

## Rollback

Rollback changes application images to the previously recorded digests. Do not reverse an applied Flyway migration or
restore a database merely to roll back application code. The previous version must remain compatible with the new
schema under the migration rule above.

Stop the rollout and restore the previous digests when readiness does not stabilize, required workflows fail, or error
and latency signals exceed the approved release threshold. Verify the same health and controlled workflows after the
rollback. Record the release identifier, image digests, migration version, reason, timestamps, operator, and evidence.

A data restore is an incident-recovery operation, not a deployment rollback. Follow the [database recovery
runbook](database-recovery.md) only when data loss or corruption has been confirmed and the recovery point is approved.

## Maintained lifecycle test

`scripts/verify-graceful-shutdown.sh` creates a controlled database lock, starts an occupancy request that remains in
flight, sends `SIGTERM` through Docker Compose, and proves that the request completes before the backend exits cleanly.
It then restarts the backend and verifies readiness. Runtime CI runs the drill against disposable local data.

The test establishes the application shutdown contract. Platform-specific routing removal, surge capacity, pod or task
budgets, secret rotation, image signing, and network policies require separate verification in the selected deployment
environment.
