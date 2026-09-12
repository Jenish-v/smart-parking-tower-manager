# Performance Testing

The repository maintains a bounded k6 workload for the 7,200-space reference facility. It is a regression gate for the
local runtime, not a production capacity claim or substitute for environment-specific testing.

## Workload

`performance/parking-read.js` runs concurrent occupancy snapshot requests against the backend. Every response must
retain the complete six-floor, 7,200-space reference model. The default run uses 10 virtual users for 20 seconds with a
short pacing delay.

The test fails unless all of these conditions hold:

- more than 99 percent of response checks pass
- fewer than 1 percent of HTTP requests fail
- 95 percent of request durations remain below 1,000 milliseconds
- at least 100 complete iterations run

These thresholds are intentionally tolerant of shared continuous-integration hardware. They detect functional errors,
severe query regressions, and stalled throughput without representing a service-level objective.

## Run locally

Start the complete runtime, then execute the load profile:

```bash
docker compose up --build --detach --wait
scripts/run-load-test.sh
```

Override concurrency and duration when a controlled environment has sufficient resources:

```bash
LOAD_VUS=25 LOAD_DURATION=60s scripts/run-load-test.sh
```

The command uses the pinned k6 container and the existing Compose network. It does not require a local k6 installation.
Normal `docker compose up` does not start the load generator because the service belongs to the `load` profile.

## Interpreting results

Record the source revision, image digests, host resources, database state, virtual-user count, duration, request count,
failure rate, and latency percentiles when comparing runs. Compare results only when these conditions are equivalent.

Before production deployment, define traffic models from measured usage and test entry, exit, reservation, receipt,
audit, and live-update paths in a production-like environment. Include realistic data volume, authentication, network
latency, autoscaling, telemetry overhead, and failure injection. Approve capacity and service-level objectives from
that evidence rather than the local regression threshold.
