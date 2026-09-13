# Security Review

This review covers the repository and maintained local runtime at the end of Milestone 13. It does not approve a
specific production environment or replace threat modelling after a deployment platform and identity provider are
selected.

## Automated controls

Security CI analyzes Java, JavaScript, and TypeScript with CodeQL on pull requests and changes to `main`. Trivy scans
the repository lockfiles, configuration, and secrets for high or critical findings. Dependabot tracks Maven, npm,
Compose images, backend and frontend container bases, and GitHub Actions each week. Updates remain ordinary pull
requests and must pass the maintained verification suite before merge.

Code analysis and dependency metadata are complementary. A passing workflow does not prove the absence of a
vulnerability, validate deployment configuration, or assess an external identity provider.

## Runtime boundaries

The local Compose runtime binds the dashboard, backend, and Jaeger viewer only to `127.0.0.1`. PostgreSQL has no host
port. The backend runs as a fixed unprivileged user with a read-only root filesystem, a temporary `/tmp`, all Linux
capabilities dropped, and privilege escalation disabled. The dashboard sends headers that prevent MIME sniffing and
framing, suppress referrer data, and disable camera, microphone, and geolocation access.

These controls protect local development from accidental network exposure. They are not an internet-facing ingress.
Production still requires TLS, network policy, rate limits, request-size limits, trusted proxy configuration, managed
secrets, image provenance, vulnerability response, and tested identity-provider settings.

## Application controls reviewed

- OIDC JWT validation fails closed outside the local profile.
- API roles separate operator actions from administrator-only adjustments and audit history.
- Sensitive adjustment actors come from the verified token subject and are audited atomically.
- Input validation and stable problem responses avoid exposing stack details.
- JDBC access uses bound parameters; migrations enforce uniqueness and lifecycle constraints.
- Request logs exclude query strings, bodies, credentials, vehicle identifiers, and actor subjects.
- Metrics avoid unbounded personal or operational identifiers and require administrator access when secured.
- Backup files are classified as sensitive and protected by integrity checks and explicit restore confirmation.

## Open deployment decisions

The following items remain outside repository-level approval:

- identity-provider registration, issuer trust, token lifetime, key rotation, and administrator assignment
- TLS certificates, ingress policy, rate limits, denial-of-service controls, and web application firewall policy
- production database encryption, backup retention, point-in-time recovery, and access review
- image signing, registry admission policy, secret rotation, and infrastructure audit retention
- legal retention periods for vehicle, session, receipt, adjustment, and audit records

Do not describe the project as production-approved until these controls are implemented and evidenced for the selected
environment.
