# Operator Dashboard

The operator dashboard is a React and TypeScript browser application. It provides a responsive application shell,
reference-facility occupancy, parking entry and exit, vehicle session search, and reservation creation, history, and
cancellation through typed API clients. Occupancy uses a server-sent event stream, falls back to 15-second refresh
while disconnected, and can be refreshed manually. A completed exit presents the immutable receipt total and reference
returned by the backend.

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

The root `compose.yaml` builds the production frontend image, serves it through Nginx on `http://localhost:5173`, and
proxies API, OpenAPI, and server-sent occupancy traffic to the backend service.

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
