#!/usr/bin/env bash

set -Eeuo pipefail

facility_id=d936bb7d-3027-47aa-a47b-d04a37e07310
work_directory=$(mktemp -d)
backend_container=$(docker compose ps --quiet backend)

if [[ -z $backend_container ]]; then
  echo "backend container is not running" >&2
  exit 1
fi

restart_required=false
cleanup() {
  if [[ $restart_required == true ]]; then
    docker compose up --detach backend >/dev/null 2>&1 || true
  fi
  rm -rf "$work_directory"
}
trap cleanup EXIT

docker compose exec -T postgres psql \
  --username=smart_parking \
  --dbname=smart_parking \
  --set=ON_ERROR_STOP=1 \
  --command='BEGIN; LOCK TABLE parking_spaces IN ACCESS EXCLUSIVE MODE; SELECT pg_sleep(8); COMMIT;' \
  > "$work_directory/database-lock.log" &
lock_process=$!

lock_acquired=false
for attempt in $(seq 1 40); do
  lock_acquired=$(docker compose exec -T postgres psql \
    --username=smart_parking \
    --dbname=smart_parking \
    --tuples-only \
    --no-align \
    --command="SELECT EXISTS (
      SELECT 1
      FROM pg_locks locks
      JOIN pg_class relations ON relations.oid = locks.relation
      WHERE relations.relname = 'parking_spaces'
        AND locks.mode = 'AccessExclusiveLock'
        AND locks.granted
    );")
  if [[ $lock_acquired == "t" ]]; then
    break
  fi
  sleep 0.25
done

if [[ $lock_acquired != "t" ]]; then
  echo "failed to create a controlled in-flight request" >&2
  exit 1
fi

curl --fail --silent --show-error --max-time 25 \
  "http://localhost:8080/api/v1/facilities/${facility_id}/occupancy" \
  > "$work_directory/occupancy.json" &
request_process=$!

sleep 1
restart_required=true
docker compose stop --timeout 30 backend

wait "$lock_process"
if ! wait "$request_process"; then
  echo "in-flight request was interrupted during shutdown" >&2
  exit 1
fi

jq --exit-status \
  '.totalSpaces == 7200 and .availableSpaces == 7200' \
  "$work_directory/occupancy.json" \
  > /dev/null

exit_code=$(docker inspect --format='{{.State.ExitCode}}' "$backend_container")
if [[ $exit_code != "0" ]]; then
  echo "backend exited with status $exit_code" >&2
  exit 1
fi

docker compose up --detach --wait backend
restart_required=false

curl --fail --silent --show-error \
  http://localhost:8080/actuator/health/readiness \
  > /dev/null

echo "graceful shutdown drill passed"
