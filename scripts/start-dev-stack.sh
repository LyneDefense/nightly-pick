#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

echo "Starting PostgreSQL..."
(
  cd "$ROOT_DIR/nightly-pick-server"
  docker compose up -d
)

echo "Starting agent..."
(
  cd "$ROOT_DIR/nightly-pick-agent"
  ./scripts/run-dev.sh
) &

AGENT_PID=$!

echo "Starting server..."
(
  cd "$ROOT_DIR/nightly-pick-server"
  ./scripts/run-local.sh
) &

SERVER_PID=$!

trap 'kill $AGENT_PID $SERVER_PID 2>/dev/null || true' EXIT

wait
