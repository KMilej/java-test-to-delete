#!/usr/bin/env bash
set -euo pipefail

have() { command -v "$1" >/dev/null 2>&1; }
die() { echo "ERROR: $*" >&2; exit 1; }
info(){ echo "info $*"; }
warn(){ echo "warn $*"; }

HOST_PORT="${HOST_PORT:-8080}"
BASE_URL="http://localhost:${HOST_PORT}"
HEALTH_PATH="${HEALTH_PATH:-/}"           # e.g. /actuator/health
WAIT_RETRIES="${WAIT_RETRIES:-40}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"
APP_SERVICE="${APP_SERVICE:-app}"

# Show Approov API domains (optional)
if have approov; then approov api -list || true; fi

# --- Required tools (binaries only; daemon checked later)
have docker || die "Docker CLI is required"
docker compose version >/dev/null 2>&1 || die "'docker compose' v2 required"
have approov || die "Approov CLI is required by test.sh"

[[ -f "$COMPOSE_FILE" ]] || die "$COMPOSE_FILE not found in $(pwd)"
[[ -f "./test.sh" ]] || die "test.sh not found in $(pwd)"

# --- Ensure Docker engine is running (start Colima/Docker Desktop if needed)
ensure_engine() {
  if docker info >/dev/null 2>&1; then
    return 0
  fi

  if have colima; then
    info "Starting Colima..."
    colima start || die "Failed to start Colima"
    # re-check
    docker info >/dev/null 2>&1 || die "Docker daemon still not running after starting Colima"
    return 0
  fi

  # macOS: try to start Docker Desktop
  if [[ "${OSTYPE:-}" == darwin* ]]; then
    warn "Docker daemon not running. Attempting to start Docker Desktop…"
    open -ga Docker || true
    for i in {1..60}; do
      if docker info >/dev/null 2>&1; then
        info "Docker Desktop is up."
        return 0
      fi
      sleep 2
    done
    die "Docker daemon not running. Please start Docker Desktop."
  fi

  die "Docker daemon not running. Please start your Docker engine."
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
  docker compose down -v || true
}
trap cleanup EXIT

# --- Start only the app container
info "Starting ${APP_SERVICE} (detached) with build…"
docker compose up -d --build "${APP_SERVICE}"

# --- Wait for HTTP health endpoint
info "Waiting for ${BASE_URL}${HEALTH_PATH}…" "to build, please wait"
for i in $(seq 1 "$WAIT_RETRIES"); do
  if curl -sf "${BASE_URL}${HEALTH_PATH}" >/dev/null 2>&1; then
    info "App is up"
    break
  fi
  printf "wait - attempt %d/%d\r" "$i" "$WAIT_RETRIES"
  sleep 2
  [[ $i -eq $WAIT_RETRIES ]] && echo && die "Service not responding on ${BASE_URL}${HEALTH_PATH}"
done

# --- Run tests on the host
info "Running tests (host)…"
BASE_URL="${BASE_URL}" bash ./test.sh
rc=$?
info "Tests finished with code: $rc"

echo
echo "App is running at: ${BASE_URL}/"
echo "Containers will be stopped now (trap)."
exit $rc
