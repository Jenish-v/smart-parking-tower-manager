# Operational Observability

The backend exposes machine-readable metrics and logs without binding deployment to a specific monitoring vendor.
Tracing export is not yet configured.

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

## Local verification

Start the stack and generate a request:

```bash
docker compose up --build --detach --wait
curl --fail http://localhost:8080/actuator/health/readiness
curl --fail http://localhost:8080/actuator/prometheus
docker compose logs --no-log-prefix backend
```

Each backend log line should be valid JSON. The request-completion record should contain `ecs.version` and
`request.id`. Stop and remove the local data volume with `docker compose down --volumes` when verification is complete.

## Remaining work

Milestone 13 still requires trace propagation and export, deployment-specific alerting, backup and recovery exercises,
load testing, graceful-shutdown verification, and the final dependency and security review.
