# Operator Dashboard

The operator dashboard is a React and TypeScript browser application. This baseline provides the responsive application
shell, route structure, typed parking-session API client, and reference-facility overview. Entry, exit, search, and live
occupancy remain intentionally unavailable until Milestone 9 connects those workflows to backend state.

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
`http://localhost:8080`. Set `VITE_API_BASE_URL` only when the API is hosted on another origin. That deployment must
also configure an appropriate cross-origin policy.

## Verification

Run linting, component and client tests, TypeScript compilation, and the production build:

```bash
npm run check
```

Individual commands are `npm run lint`, `npm run test`, and `npm run build`. Generated output is written to `dist/`
and is not committed.

## Structure

```text
src/api/          Typed HTTP access and API problem handling
src/components/   Shared layout and presentation components
src/pages/        Route-level operator views
src/test/         Browser-test setup
```

Component conventions are maintained in [docs/frontend/component-standards.md](../docs/frontend/component-standards.md).
