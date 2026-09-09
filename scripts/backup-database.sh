#!/usr/bin/env bash

set -Eeuo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 BACKUP_FILE" >&2
  exit 64
fi

backup_file=$1
checksum_file="${backup_file}.sha256"

if [[ -e "$backup_file" || -e "$checksum_file" ]]; then
  echo "refusing to overwrite an existing backup or checksum" >&2
  exit 73
fi

for command in docker sha256sum; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "required command not found: $command" >&2
    exit 69
  fi
done

mkdir -p "$(dirname "$backup_file")"
umask 077

partial_file="${backup_file}.partial.$$"
partial_checksum="${checksum_file}.partial.$$"
trap 'rm -f "$partial_file" "$partial_checksum"' EXIT

docker compose exec -T postgres pg_dump \
  --username=smart_parking \
  --dbname=smart_parking \
  --format=custom \
  --compress=9 \
  --no-owner \
  --no-privileges \
  > "$partial_file"

docker compose exec -T postgres pg_restore --list < "$partial_file" > /dev/null

digest=$(sha256sum "$partial_file" | awk '{print $1}')
printf '%s  %s\n' "$digest" "$(basename "$backup_file")" > "$partial_checksum"

mv "$partial_file" "$backup_file"
mv "$partial_checksum" "$checksum_file"
trap - EXIT

echo "backup written to $backup_file"
