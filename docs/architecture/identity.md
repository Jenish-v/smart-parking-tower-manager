# Identity and Authorization

## Boundary

The backend is an OAuth 2.0 resource server. With security enabled, it discovers signing keys and validation metadata
from the configured OpenID Connect issuer, validates bearer JWT signatures and issuer claims, and creates no server-side
login session. Health probes and the OpenAPI document remain public. Every `/api/v1` operation requires an authenticated
principal.

Security is enabled by default outside the `local` profile. Startup fails when no issuer URI is configured, preventing
an accidentally anonymous non-local deployment. The maintained local profile disables enforcement so a fresh Docker
Compose checkout remains self-contained while browser login integration is unfinished.

## Roles

The JWT `roles` claim supplies application roles. Values are mapped to Spring Security authorities with a `ROLE_`
prefix. The maintained roles are:

| Role | Access |
| --- | --- |
| `OPERATOR` | Occupancy, parking-session, reservation, and receipt reads; parking and reservation commands |
| `ADMIN` | All operator access plus fee adjustments |

Unknown roles grant no API access. Missing or invalid authentication returns `AUTHENTICATION_REQUIRED`; insufficient
role membership returns `ACCESS_DENIED`. Both use the same problem-detail shape as application errors.

## Configuration

Set these variables for a secured runtime:

```text
SECURITY_ENABLED=true
OIDC_ISSUER_URI=https://identity.example.com/realms/smart-parking
```

The issuer must publish standard OpenID Provider metadata and a JSON Web Key Set. Token acquisition, provider
provisioning, dashboard authorization-code flow, facility-scoped claims, and durable audit records remain Milestone 12
work. Until those pieces are implemented, the local dashboard is a development workflow and the system is not ready for
an internet-facing deployment.
