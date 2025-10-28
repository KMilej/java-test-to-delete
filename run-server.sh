#!/usr/bin/env bash
set -euo pipefail

# show Approov API domains
approov api -list || true

HOST_PORT="${HOST_PORT:-8080}"
BASE_URL="http://localhost:${HOST_PORT}"
WAIT_RETRIES="${WAIT_RETRIES:-40}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"

# Check if Colima is running; if not, start it automatically
if ! colima status >/dev/null 2>&1; then
  echo "Colima is not running. Starting Colima..."
  colima start
  if [ $? -ne 0 ]; then
    echo " Failed to start Colima. Please start it manually."
    exit 1
  fi
else
  echo " Colima is already running."
fi

have() { command -v "$1" >/dev/null 2>&1; }
die() { echo "ERROR: $*" >&2; exit 1; }
info(){ echo "info $*"; }
warn(){ echo "warn $*"; }

ensure_approov_cli() {
  if ! have approov; then
    die "Approov CLI is not installed or not in PATH. It is REQUIRED to run this script."
  fi
}

ensure_docker_v2() {
  # 1) Docker CLI present
  if ! have docker; then
    die "Docker CLI is not installed or not in PATH. Docker (CLI + running Engine/daemon) is REQUIRED."
  fi

  # 2) 'docker compose' (v2)
  if ! docker compose version >/dev/null 2>&1; then
    die "'docker compose' (Docker Compose v2) is not available. Install the Compose v2 plugin or use Docker Desktop, which bundles it."
  fi

  # 3) Check if Docker daemon is running
  if ! docker version >/dev/null 2>&1; then
    warn "Docker daemon is not reachable (is the engine running?)."
    if have colima; then
      warn "Colima is installed. Start it with: 'colima start' and re-run the script."
    fi
    die "Docker Engine is not running. Start Docker Desktop or Colima, then re-run."
  fi
}

print_versions() {
  echo "== Versions =="
  docker version --format '{{.Client.Version}} (client)' || docker version || true
  docker compose version || true
}

wait_for_app() {
  info "Waiting for ${BASE_URL}/ to respond…"
  for i in $(seq 1 "$WAIT_RETRIES"); do
    if curl -sf "${BASE_URL}/" >/dev/null 2>&1; then
      info "App is up "
      return 0
    fi
    printf "wait - attempt %d/%d\r" "$i" "$WAIT_RETRIES"
    sleep 2
  done
  echo
  die "Service not responding on ${BASE_URL}/"
}

run_tests_host() {
  info "Running tests on host (not in container)…"
  BASE_URL="${BASE_URL}" bash ./test.sh
  info "Tests finished "
}

# -------- main --------
# 0) sanity checks
[[ -f "$COMPOSE_FILE" ]] || die "$COMPOSE_FILE not found in $(pwd)"
[[ -f "./test.sh" ]] || die "test.sh not found in $(pwd)"
[[ -f "./gradlew" ]] || warn "gradlew not found — ensure your compose runs bootRun inside the container"

# 1) required tools (NO INSTALLS)
ensure_approov_cli
ensure_docker_v2
print_versions

# 2) build & start container (detached) — ONLY v2
info "Starting containers (detached) with build…"
docker compose up -d --build

# 3) wait until the app is ready on localhost
wait_for_app

# 4) run tests on the host
run_tests_host

echo
echo "App is running at: ${BASE_URL}/"
echo "To stop containers: docker compose down"

# Turn off running containers after tests
docker ps
docker stop app-approov || warn "Could not stop 'app-approov'. Check service name or use 'docker compose down'."
docker stop test-approov || warn "Could not stop 'tests-approov'. Check service name or use 'docker compose down'."
