#!/usr/bin/env bash
set -euo pipefail

have() { command -v "$1" >/dev/null 2>&1; }
die() { echo "ERROR: $*" >&2; exit 1; }
info(){ echo "info $*"; }
warn(){ echo "warn $*"; }

# --- Configuration and auto port detection ---
# If HOST_PORT is set (env), only that port will be used.
# Otherwise, try 8080 first, 80XX depends on which one is needed.
HOST_PORT="${HOST_PORT:-}"
HEALTH_PATH="${HEALTH_PATH:-/}"
WAIT_RETRIES="${WAIT_RETRIES:-40}"
APP_SERVICE="${APP_SERVICE:-app}"

if [[ -n "$HOST_PORT" ]]; then
  PORT_CANDIDATES=("$HOST_PORT")
else
  PORT_CANDIDATES=(8080 8070)
fi

# Temporary BASE_URL (will be updated after successful probe)
BASE_URL="http://localhost:${HOST_PORT:-8080}"

# Compose file detection
COMPOSE_FILE="${COMPOSE_FILE:-}"
if [[ -z "${COMPOSE_FILE}" ]]; then
  if   [[ -f "compose.yaml" ]]; then COMPOSE_FILE="compose.yaml"
  elif [[ -f "compose.yml"  ]]; then COMPOSE_FILE="compose.yml"
  elif [[ -f "docker-compose.yml" ]]; then COMPOSE_FILE="docker-compose.yml"
  else
    die "No compose file found (compose.yaml|compose.yml|docker-compose.yml)"
  fi
fi
info "Using compose file: ${COMPOSE_FILE}"

# Optional: list Approov API domains
if have approov; then approov api -list ; fi

# Required tools
have docker || die "Docker CLI is required"
docker compose version >/dev/null 2>&1 || die "'docker compose' v2 required"
have approov || die "Approov CLI is required by test.sh"

[[ -f "$COMPOSE_FILE" ]] || die "$COMPOSE_FILE not found in $(pwd)"
[[ -f "./test.sh" ]] || die "test.sh not found in $(pwd)"

# Ensure Docker engine is running
ensure_engine() {
  if docker info >/dev/null 2>&1; then
    return 0
  fi

  if have colima; then
    info "Starting Colima..."
    colima start || die "Failed to start Colima"
    docker info >/dev/null 2>&1 || die "Docker daemon still not running after starting Colima"
    return 0
  fi

  # Docker Desktop (disabled)
  # if [[ "${OSTYPE:-}" == darwin* ]]; then
  #   warn "Docker daemon not running. Attempting to start Docker Desktop…"
  #   open -ga Docker || true
  #   for i in {1..60}; do
  #     if docker info >/dev/null 2>&1; then
  #       info "Docker Desktop is up."
  #       return 0
  #     fi
  #     sleep 2
  #   done
  #   die "Docker daemon not running. Please start Docker Desktop."
  # fi

  die "Docker daemon not running. Please start your Docker engine (Colima recommended)."
}

ensure_engine

print_versions() {
  echo "== Versions =="
  docker version --format '{{.Client.Version}} (client)' || true
  docker compose version || true
}
print_versions

cleanup() {
  info "Shutting down environment…"
  docker compose -f "$COMPOSE_FILE" down -v || true
}
trap cleanup EXIT

# Start only the app container
info "Starting ${APP_SERVICE} (detached) with build…"
docker compose -f "$COMPOSE_FILE" up -d --build "${APP_SERVICE}"

# Wait for the app to become healthy (multi-port check)
info "Waiting for app to become ready on one of: ${PORT_CANDIDATES[*]} (building + starting)…"

FOUND_PORT=""
for i in $(seq 1 "$WAIT_RETRIES"); do
  for p in "${PORT_CANDIDATES[@]}"; do
    url="http://localhost:${p}${HEALTH_PATH}"
    if curl -sf --connect-timeout 2 --max-time 3 "$url" >/dev/null 2>&1; then
      FOUND_PORT="$p"
      BASE_URL="http://localhost:${FOUND_PORT}"
      info "App is up on ${BASE_URL}${HEALTH_PATH}"
      break 2
    fi
  done
  printf "wait - attempt %d/%d\r" "$i" "$WAIT_RETRIES"
  sleep 2
done
[[ -z "$FOUND_PORT" ]] && echo && die "Service not responding on any of: ${PORT_CANDIDATES[*]}${HEALTH_PATH}"

# Run tests on host
info "Running tests (host)"
BASE_URL="${BASE_URL}" bash ./test.sh
rc=$?
info "Tests finished with code: $rc"

echo
echo "App is running at: ${BASE_URL}/"
echo "Containers will be stopped now"
exit $rc