# Operational Observability

The backend exposes machine-readable metrics, traces, and logs without binding deployment to a specific monitoring
vendor. The maintained local stack exports OpenTelemetry Protocol traces to an in-memory Jaeger instance.

## Metrics

`GET /actuator/prometheus` publishes Prometheus text exposition. The registry includes JVM, process, disk, Tomcat,
HikariCP, JDBC connection, application-startup, log-event, and Spring MVC request metrics supplied by Spring Boot and
Micrometer.

When authentication is enabled, the endpoint requires a bearer token with the `ADMIN` role. Health probes remain
public so an orchestrator can make liveness and readiness decisions without credentials. Local mode disables
authentication and permits direct scraping.

Scrape labels must remain low-cardinality. Do not add vehicle identifiers, session identifiers, reservation identifiers,
request identifiers, user subjects, or raw paths as metric tags. Alert thresholds belong to the deployment repository
because capacity and latency objectives depend on the target environment.

## Structured logs

Console output uses Elastic Common Schema JSON by default. Set `CONSOLE_LOG_STRUCTURED_FORMAT` only when an approved log
collector requires another Spring Boot-supported format. `DEPLOYMENT_ENVIRONMENT` sets the ECS service environment.

Every HTTP request receives an `X-Request-ID` response header. Caller-supplied values are accepted only when they
contain 1 to 64 ASCII letters, digits, periods, underscores, or hyphens and begin with a letter or digit. Invalid or
missing values are replaced with a UUID. Request-completion events carry the identifier as `request.id` and record only:

- HTTP method
- request path without the query string
- response status
- elapsed milliseconds

Request bodies, query strings, authorization headers, tokens, actor subjects, and vehicle identifiers are not logged by
the request filter. Application logging must preserve that boundary.

## Traces

The backend creates spans through Spring Boot and Micrometer Tracing, accepts and produces W3C trace context, and
exports sampled spans over OTLP/HTTP. `OTLP_TRACING_ENDPOINT` must contain the complete collector endpoint, including
`/v1/traces`. Export is off by default and requires `TRACING_EXPORT_ENABLED=true`. `OTEL_SERVICE_NAME` identifies the
service in the trace backend. The default sampling probability is `0.1`; set `TRACING_SAMPLING_PROBABILITY` to a value
from `0.0` through `1.0` according to traffic volume and retention capacity.

The local stack samples every request so verification is deterministic. Jaeger is available at
`http://localhost:16686` and stores traces only in memory. It is a development inspection tool, not a production trace
store. A deployment must supply an authenticated, encrypted collector endpoint, an appropriate sampling policy,
retention controls, and access restrictions.

Incoming `traceparent` values join the server request to an existing distributed trace. Do not add vehicle identifiers,
session identifiers, reservation identifiers, actor subjects, tokens, request bodies, or unrestricted database
statements as span attributes.

## Local verification

Start the stack and generate a request:

```bash
docker compose up --build --detach --wait
curl --fail http://localhost:8080/actuator/health/readiness
curl --fail http://localhost:8080/actuator/prometheus
docker compose logs --no-log-prefix backend
```

Each backend log line should be valid JSON. The request-completion record should contain `ecs.version` and
`request.id`. Open `http://localhost:16686` to inspect sampled backend traces. Stop the runtime with
`docker compose down --volumes` when verification is complete.

## Remaining work

Milestone 13 still requires deployment-specific alerting and the final dependency and security review. The maintained
load test is a local regression gate and does not establish production capacity.
