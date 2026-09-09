#!/usr/bin/env bash

set -Eeuo pipefail

work_directory=$(mktemp -d)
backup_file="$work_directory/smart-parking.dump"
trap 'rm -rf "$work_directory"' EXIT

space_count() {
  docker compose exec -T postgres psql \
    --username=smart_parking \
    --dbname=smart_parking \
    --tuples-only \
    --no-align \
    --command='SELECT count(*) FROM parking_spaces;'
}

if [[ $(space_count) != "7200" ]]; then
  echo "reference facility is not ready for the recovery drill" >&2
  exit 1
fi

scripts/backup-database.sh "$backup_file"

docker compose exec -T postgres psql \
  --username=smart_parking \
  --dbname=smart_parking \
  --set=ON_ERROR_STOP=1 \
  --command='CREATE TABLE recovery_probe (id integer PRIMARY KEY); INSERT INTO recovery_probe VALUES (1);'

SMART_PARKING_RESTORE_CONFIRM=restore-smart-parking \
  scripts/restore-database.sh "$backup_file"

if [[ $(space_count) != "7200" ]]; then
  echo "restored reference facility has an unexpected space count" >&2
  exit 1
fi

probe_table=$(docker compose exec -T postgres psql \
  --username=smart_parking \
  --dbname=smart_parking \
  --tuples-only \
  --no-align \
  --command="SELECT to_regclass('public.recovery_probe') IS NULL;")
if [[ $probe_table != "t" ]]; then
  echo "post-backup mutation survived the restore" >&2
  exit 1
fi

curl --fail --silent --show-error \
  http://localhost:8080/actuator/health/readiness \
  > /dev/null

echo "database recovery drill passed"
