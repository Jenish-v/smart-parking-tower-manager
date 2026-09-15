#!/usr/bin/env bash

set -Eeuo pipefail

base_url=${BASE_URL:-http://localhost:8080}
facility_id=${FACILITY_ID:-d936bb7d-3027-47aa-a47b-d04a37e07310}
api="$base_url/api/v1/facilities/$facility_id"
reservation_id=$(cat /proc/sys/kernel/random/uuid)
cancellation_id=$(cat /proc/sys/kernel/random/uuid)
entry_key=$(cat /proc/sys/kernel/random/uuid)
exit_key=$(cat /proc/sys/kernel/random/uuid)
adjustment_id=$(cat /proc/sys/kernel/random/uuid)
vehicle="RC-${reservation_id:0:8}"
cancel_vehicle="RC-CANCEL-${cancellation_id:0:8}"
vehicle=${vehicle^^}
cancel_vehicle=${cancel_vehicle^^}
work_directory=$(mktemp -d)
trap 'rm -rf "$work_directory"' EXIT

assert_json() {
  local document=$1
  local expression=$2
  local message=$3
  if ! jq --exit-status "$expression" <<< "$document" > /dev/null; then
    echo "$message" >&2
    jq . <<< "$document" >&2
    exit 1
  fi
}

initial_occupancy=$(curl --fail --silent --show-error "$api/occupancy")
initial_occupied=$(jq --raw-output '.occupiedSpaces' <<< "$initial_occupancy")
initial_available=$(jq --raw-output '.availableSpaces' <<< "$initial_occupancy")

starts_at=$(date --utc --date='5 seconds' +%Y-%m-%dT%H:%M:%SZ)
ends_at=$(date --utc --date='10 minutes' +%Y-%m-%dT%H:%M:%SZ)
reservation_body=$(jq --null-input --compact-output \
  --arg vehicle "$vehicle" \
  --arg starts "$starts_at" \
  --arg ends "$ends_at" \
  '{vehicleIdentifier: $vehicle, requiredSize: "SMALL", startsAt: $starts, endsAt: $ends}')

reservation=$(curl --fail --silent --show-error \
  --request PUT \
  --header 'Content-Type: application/json' \
  --data "$reservation_body" \
  "$api/reservations/$reservation_id")
assert_json "$reservation" \
  ".reservationId == \"$reservation_id\" and .vehicleIdentifier == \"$vehicle\" and .status == \"CONFIRMED\"" \
  "reservation creation did not return the confirmed reservation"
reservation_created_at=$(jq --raw-output '.createdAt' <<< "$reservation")

reservation_replay=$(curl --fail --silent --show-error \
  --request PUT \
  --header 'Content-Type: application/json' \
  --data "$reservation_body" \
  "$api/reservations/$reservation_id")
assert_json "$reservation_replay" \
  ".reservationId == \"$reservation_id\" and .createdAt == \"$reservation_created_at\"" \
  "reservation replay changed the stored reservation"

sleep 6
entry_body=$(jq --null-input --compact-output \
  --arg vehicle "$vehicle" \
  '{vehicleIdentifier: $vehicle, requiredSize: "SMALL"}')
entry=$(curl --fail --silent --show-error \
  --request POST \
  --header 'Content-Type: application/json' \
  --header "Idempotency-Key: $entry_key" \
  --data "$entry_body" \
  "$api/parking-sessions/entries")
assert_json "$entry" \
  ".status == \"ACTIVE\"
    and .reservationId == \"$reservation_id\"
    and .space.floorNumber == 1
    and .space.zoneCode == \"A\"
    and .space.spaceNumber == 1" \
  "entry did not fulfill the reservation in the first deterministic zone"
session_id=$(jq --raw-output '.sessionId' <<< "$entry")

entry_replay=$(curl --fail --silent --show-error \
  --request POST \
  --header 'Content-Type: application/json' \
  --header "Idempotency-Key: $entry_key" \
  --data "$entry_body" \
  "$api/parking-sessions/entries")
assert_json "$entry_replay" ".sessionId == \"$session_id\"" \
  "entry replay returned a different session"

conflict_status=$(curl --silent --show-error \
  --output "$work_directory/conflict.json" \
  --write-out '%{http_code}' \
  --request POST \
  --header 'Content-Type: application/json' \
  --header "Idempotency-Key: $entry_key" \
  --data '{"vehicleIdentifier":"RC-CONFLICT","requiredSize":"SMALL"}' \
  "$api/parking-sessions/entries")
if [[ $conflict_status != "409" ]]; then
  echo "idempotency conflict returned HTTP $conflict_status instead of 409" >&2
  exit 1
fi
assert_json "$(< "$work_directory/conflict.json")" '.code == "IDEMPOTENCY_CONFLICT"' \
  "idempotency conflict did not return the stable problem code"

active=$(curl --fail --silent --show-error \
  --get \
  --data-urlencode "vehicleIdentifier=$vehicle" \
  "$api/parking-sessions/active")
assert_json "$active" ".sessionId == \"$session_id\" and .status == \"ACTIVE\"" \
  "active-session lookup did not return the admitted vehicle"

occupied=$(curl --fail --silent --show-error "$api/occupancy")
assert_json "$occupied" \
  ".occupiedSpaces == ($initial_occupied + 1) and .availableSpaces == ($initial_available - 1)" \
  "occupancy did not reflect the admitted vehicle"

fulfilled=$(curl --fail --silent --show-error "$api/reservations/$reservation_id")
assert_json "$fulfilled" '.status == "FULFILLED" and .resolvedAt != null' \
  "arrival did not persist reservation fulfillment"

exit_body=$(jq --null-input --compact-output --arg vehicle "$vehicle" '{vehicleIdentifier: $vehicle}')
parking_exit=$(curl --fail --silent --show-error \
  --request POST \
  --header 'Content-Type: application/json' \
  --header "Idempotency-Key: $exit_key" \
  --data "$exit_body" \
  "$api/parking-sessions/exits")
assert_json "$parking_exit" \
  ".sessionId == \"$session_id\" and .status == \"COMPLETED\" and .receipt.currency == \"CAD\"" \
  "exit did not complete the session with a CAD receipt"
receipt_id=$(jq --raw-output '.receipt.receiptId' <<< "$parking_exit")

exit_replay=$(curl --fail --silent --show-error \
  --request POST \
  --header 'Content-Type: application/json' \
  --header "Idempotency-Key: $exit_key" \
  --data "$exit_body" \
  "$api/parking-sessions/exits")
assert_json "$exit_replay" \
  ".sessionId == \"$session_id\" and .receipt.receiptId == \"$receipt_id\"" \
  "exit replay changed the completed session or receipt"

adjustment_body='{"amountMinor":100,"reason":"OPERATIONAL_EXCEPTION","reasonDetail":"Release acceptance probe"}'
statement=$(curl --fail --silent --show-error \
  --request PUT \
  --header 'Content-Type: application/json' \
  --data "$adjustment_body" \
  "$api/parking-sessions/$session_id/receipt/adjustments/$adjustment_id")
assert_json "$statement" \
  ".receiptId == \"$receipt_id\"
    and .adjustedTotalMinor == (.baseTotalMinor + 100)
    and (.adjustments | length) == 1
    and .adjustments[0].actorSubject == \"local-development\"" \
  "fee adjustment was not appended to the receipt statement"

audit=$(curl --fail --silent --show-error "$api/audit-events?limit=100")
assert_json "$audit" \
  "any(.action == \"FEE_ADJUSTMENT_APPENDED\"
    and .targetType == \"RECEIPT\"
    and .targetId == \"$receipt_id\"
    and .actorSubject == \"local-development\")" \
  "fee adjustment audit event was not found"

restored=$(curl --fail --silent --show-error "$api/occupancy")
assert_json "$restored" \
  ".occupiedSpaces == $initial_occupied and .availableSpaces == $initial_available" \
  "occupancy did not return to its initial values after exit"

cancel_starts=$(date --utc --date='15 minutes' +%Y-%m-%dT%H:%M:%SZ)
cancel_ends=$(date --utc --date='30 minutes' +%Y-%m-%dT%H:%M:%SZ)
cancel_body=$(jq --null-input --compact-output \
  --arg vehicle "$cancel_vehicle" \
  --arg starts "$cancel_starts" \
  --arg ends "$cancel_ends" \
  '{vehicleIdentifier: $vehicle, requiredSize: "MEDIUM", startsAt: $starts, endsAt: $ends}')
curl --fail --silent --show-error \
  --request PUT \
  --header 'Content-Type: application/json' \
  --data "$cancel_body" \
  "$api/reservations/$cancellation_id" \
  > /dev/null
cancelled=$(curl --fail --silent --show-error \
  --request DELETE \
  "$api/reservations/$cancellation_id")
assert_json "$cancelled" '.status == "CANCELLED" and .resolvedAt != null' \
  "reservation cancellation did not persist the terminal state"

echo "release acceptance workflow passed"
