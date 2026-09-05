# ADR-0003: Use OpenID Connect Bearer Tokens

## Status

Accepted.

## Context

The API needs externally managed operator identity, short-lived credentials, role enforcement, and a principal that can
later be attached to audit records. The project should not store passwords or implement a custom token format.

## Decision

The backend will act as a stateless OAuth 2.0 resource server and validate JWT access tokens against one configured
OpenID Connect issuer. Application roles come from the token's `roles` claim. `OPERATOR` covers normal parking and
reservation work; `ADMIN` adds sensitive fee adjustment access.

Provider selection and provisioning remain deployment concerns. The backend depends only on issuer discovery and
standard JWT validation. Browser authentication will use the authorization-code flow with Proof Key for Code Exchange.

## Consequences

Non-local startup fails without issuer configuration. Token revocation follows issuer policy and token lifetime; the
backend does not maintain login sessions. Deployments must protect issuer and audience configuration, rotate signing
keys through provider metadata, and avoid logging tokens. Authorization tests must cover anonymous, operator, and
administrator boundaries.
