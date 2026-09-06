# Operator Dashboard

The operator dashboard is a React and TypeScript browser application. It provides a responsive application shell,
reference-facility occupancy, parking entry and exit, vehicle session search, and reservation creation, history, and
cancellation through typed API clients. It supports OpenID Connect authorization code login with PKCE and attaches the
current bearer token to API and occupancy-stream requests. Occupancy uses a server-sent event stream, falls back to
15-second refresh while disconnected, and can be refreshed manually. A completed exit presents the immutable receipt
total and reference returned by the backend. Vehicle history also presents the immutable base receipt total for
completed sessions. The dashboard does not yet expose the administrative adjustment command; the typed client supports
that API for the identity and authorization work that must precede an operator control.

## Requirements

- Node.js 22.12 or newer
- npm 11 or newer
- The backend on port 8080 when working with API calls

## Setup

Install the locked dependencies:

```bash
npm ci
```

Run the development server:

```bash
npm run dev
```

Vite serves the dashboard on `http://localhost:5173` and proxies `/api` and `/openapi.yaml` to
`http://localhost:8080`. The dashboard targets the reference facility by default. Set `VITE_FACILITY_ID` for another
configured facility. Set `VITE_API_BASE_URL` only when the API is hosted on another origin; that deployment must also
configure an appropriate cross-origin policy.

Configure a secured deployment at frontend build time:

```text
VITE_OIDC_AUTHORITY=https://identity.example.com/realms/smart-parking
VITE_OIDC_CLIENT_ID=smart-parking-dashboard
VITE_OIDC_SCOPE=openid profile
```

The authority and client ID must be set together. Register `${dashboardOrigin}/auth/callback` as an allowed redirect URI
and the dashboard origin as an allowed post-logout redirect URI. The client must be public, require authorization code
flow with PKCE, and receive an access token whose issuer and `roles` claim satisfy the backend configuration. Vite
variables are public build inputs and must not contain client secrets. The Dockerfile accepts the same names as build
arguments.

The root `compose.yaml` builds the production frontend image, serves it through Nginx on `http://localhost:5173`, and
proxies API, OpenAPI, and server-sent occupancy traffic to the backend service.

The maintained local backend profile disables authentication, and the dashboard remains in local mode when the OIDC
variables are absent. This keeps the self-contained Compose stack runnable without an external identity provider.

## Verification

Run linting, component and client tests, TypeScript compilation, and the production build:

```bash
npm run check
```

Individual commands are `npm run lint`, `npm run test`, and `npm run build`. Generated output is written to `dist/`
and is not committed.

## Structure

```text
src/api/          Shared HTTP handling and typed workflow clients
src/components/   Shared layout and presentation components
src/hooks/        Stateful API refresh and lifecycle coordination
src/pages/        Route-level operator views
src/test/         Browser-test setup
```

Component conventions are maintained in [docs/frontend/component-standards.md](../docs/frontend/component-standards.md).
