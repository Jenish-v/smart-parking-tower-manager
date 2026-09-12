#!/usr/bin/env bash

set -Eeuo pipefail

export LOAD_VUS=${LOAD_VUS:-10}
export LOAD_DURATION=${LOAD_DURATION:-20s}

docker compose --profile load run --rm \
  --no-deps \
  load-test
