#!/usr/bin/env bash
set -euo pipefail

# /* PROPERTIES */
HOST_PORT="${HOST_PORT:-8002}"                # host port -> container 8002
BASE_URL="http://localhost:${HOST_PORT}"      # where tests will hit
WAIT_RETRIES="${WAIT_RETRIES:-40}"            # ~80s (2s * 40)
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"

# /* METHODS */
have() { command -v "$1" >/dev/null 2>&1; }
die() { echo "ERROR: $*" >&2; exit 1; }
info(){ echo "[info] $*"; }
warn(){ echo "[warn] $*"; }

# ---- REQUIREMENTS CHECKS (no installation) ----
ensure_approov_cli() {
  # Change 'approov' to the actual command name if yours is different
  if ! have approov; then
    die "Approov CLI is not installed or not in PATH. It is REQUIRED to run this script."
  fi
}

ensure_docker_v2() {
  # 1) Docker CLI present?
  if ! have docker; then
    die "Docker CLI is not installed or not in PATH. Docker (CLI + running Engine/daemon) is REQUIRED."
  fi

  # 2) 'docker compose' (v2) available as a subcommand?
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
  echo "=============="
}

wait_for_app() {
  info "Waiting for ${BASE_URL}/ to respond…"
  for i in $(seq 1 "$WAIT_RETRIES"); do
    if curl -sf "${BASE_URL}/" >/dev/null 2>&1; then
      info "App is up ✅"
      return 0
    fi
    printf "[wait] attempt %d/%d\r" "$i" "$WAIT_RETRIES"
    sleep 2
  done
  echo
  die "Service not responding on ${BASE_URL}/"
}

run_tests_host() {
  info "Running tests on host (not in container)…"
  BASE_URL="${BASE_URL}" bash ./testall.sh
  info "Tests finished ✅"
}

# -------- main --------
# 0) sanity checks
[[ -f "$COMPOSE_FILE" ]] || die "$COMPOSE_FILE not found in $(pwd)"
[[ -f "./testall.sh" ]] || die "testall.sh not found in $(pwd)"
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

# 5) optional next steps
echo
echo "[done] App is running at: ${BASE_URL}/"
echo "       To stop containers: docker compose down"

# Turn off running containers after tests
docker ps
docker stop app-1 || warn "Could not stop 'app-1'. Check service name or use 'docker compose down'."
docker stop test-1 || warn "Could not stop 'tests-1'. Check service name or use 'docker compose down'."
