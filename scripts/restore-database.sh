#!/usr/bin/env bash

set -Eeuo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 BACKUP_FILE" >&2
  exit 64
fi

backup_file=$1
checksum_file="${backup_file}.sha256"

if [[ ${SMART_PARKING_RESTORE_CONFIRM:-} != "restore-smart-parking" ]]; then
  echo "set SMART_PARKING_RESTORE_CONFIRM=restore-smart-parking to replace the local database" >&2
  exit 64
fi

if [[ ! -r "$backup_file" || ! -r "$checksum_file" ]]; then
  echo "the backup and its .sha256 checksum must both be readable" >&2
  exit 66
fi

for command in docker sha256sum; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "required command not found: $command" >&2
    exit 69
  fi
done

expected_digest=$(awk 'NR == 1 {print $1}' "$checksum_file")
actual_digest=$(sha256sum "$backup_file" | awk '{print $1}')
if [[ ! $expected_digest =~ ^[[:xdigit:]]{64}$ || "$actual_digest" != "$expected_digest" ]]; then
  echo "backup checksum verification failed" >&2
  exit 65
fi

docker compose exec -T postgres pg_restore --list < "$backup_file" > /dev/null

services_stopped=false
restart_services() {
  if [[ $services_stopped == true ]]; then
    docker compose up --detach backend frontend >/dev/null 2>&1 || true
  fi
}
trap restart_services EXIT

docker compose stop backend frontend
services_stopped=true

docker compose exec -T postgres dropdb \
  --username=smart_parking \
  --if-exists \
  --force \
  smart_parking
docker compose exec -T postgres createdb \
  --username=smart_parking \
  --owner=smart_parking \
  smart_parking
docker compose exec -T postgres pg_restore \
  --username=smart_parking \
  --dbname=smart_parking \
  --exit-on-error \
  --single-transaction \
  --no-owner \
  --no-privileges \
  < "$backup_file"

docker compose up --detach --wait backend frontend
services_stopped=false
trap - EXIT

echo "database restored from $backup_file"
