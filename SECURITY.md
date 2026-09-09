# Security Policy

## Project status

The project is pre-release. No production version is currently supported. The backend validates OpenID Connect access
tokens and enforces operator and administrator roles, and secured dashboard builds support browser login. Local mode
disables authentication. Verified actors are recorded for fee adjustments. Internet-facing deployment remains blocked
until deployment controls and the remaining operational hardening are complete.

## Reporting a vulnerability

Do not open a public issue for a suspected vulnerability. Use the repository's GitHub private vulnerability reporting
or security advisory feature. Include:

- affected component and revision
- reproduction steps
- expected and observed behaviour
- potential impact
- any known workaround

Reports will be acknowledged after they are reviewed. A disclosure timeline will be agreed upon before details are
published.

## Security requirements

- Secrets must come from the runtime environment or an approved secret store.
- Authentication and authorization checks belong at application boundaries.
- Administrative actions and parking-session overrides must be auditable.
- External input must be validated before it reaches domain operations.
- Database access must use parameterized queries or framework-managed bindings.
- Dependency updates must pass automated tests before merge.
- Logs must not contain passwords, tokens, payment details, or unnecessary personal data.
- Operational metrics must use bounded labels and remain administrator-only when authentication is enabled.
- Public deployment is blocked until deployment controls and the remaining security hardening are verified.

Security controls described as planned are not considered active until they are implemented and verified.
