#!/usr/bin/env bash
set -euo pipefail  # fail on error, undefined variable, or pipe failure

# --- Config ---
BASE_URL="http://localhost:8002"
URL_ROOT="${BASE_URL}"
URL_TOKEN_CHECK="${BASE_URL}/token-check"
URL_TOKEN_BINDING_CHECK="${BASE_URL}/token-binding-check"

# Endpoint descriptions (for readable output)
DESC_ROOT="endpoint /"
DESC_TOKEN_CHECK="endpoint /token-check"
DESC_TOKEN_BINDING_CHECK="endpoint /token-binding-check"

# --- Helpers --- check if curl, grep, approov exist
require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "ERROR: '$1' not found in PATH." >&2
    exit 127
  }
}

# curl wrapper: shows HTTP status at the end (without -i: don't show response headers)
curl_show() {
  local url="$1"
  shift
  curl -sS -w "\nHTTP Status: %{http_code}\n" "$url" "$@"
  echo
}

# Helper: describe and execute the request
run_test() {
  local desc="$1"
  local url="$2"
  shift 2
  echo "-- ${desc} (${url})"
  curl_show "$url" "$@"
}

# --- Pre-flight checks ---
require_cmd curl
require_cmd grep
require_cmd approov

echo "=============================="
echo "== 1) Tests WITHOUT any header =="
run_test "$DESC_ROOT"                 "$URL_ROOT"                 -X GET
run_test "$DESC_TOKEN_CHECK"          "$URL_TOKEN_CHECK"          -X GET
run_test "$DESC_TOKEN_BINDING_CHECK"  "$URL_TOKEN_BINDING_CHECK"  -X GET
echo "=============================="

sleep 2
echo "=============================="
echo "== 2) Requests WITH Approov-Token =="
echo "=============================="
TOKEN="$(
  approov token -genExample api.example.com \
    | grep -oE '[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+' \
    | head -n1
)"
echo "TOKEN=${TOKEN}"
echo "=============================="
echo "=============================="

echo "== 2a) Tests WITH one header: Approov-Token =="
run_test "$DESC_ROOT"                 "$URL_ROOT"                 -X GET -H "Approov-Token: ${TOKEN}"
run_test "$DESC_TOKEN_CHECK"          "$URL_TOKEN_CHECK"          -X GET -H "Approov-Token: ${TOKEN}"
run_test "$DESC_TOKEN_BINDING_CHECK"  "$URL_TOKEN_BINDING_CHECK"  -X GET -H "Approov-Token: ${TOKEN}"
echo "=============================="
sleep 5
echo "=============================="
echo "== 3) Requests WITH token binding (Authorization header) =="
VALUE="Kmilej"  # value of the bound header
BINDING_TOKEN="$(
  approov token -setDataHashInToken "${VALUE}" -genExample api.example.com \
    | grep -oE '[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+' \
    | head -n1
)"

echo "=============================="
echo "BindingToken=${BINDING_TOKEN}"
echo "=============================="
echo "=============================="
echo "== 3a) Tests WITH two headers: Approov-Token + Authorization =="
run_test "$DESC_ROOT"                 "$URL_ROOT"                 -X GET -H "Approov-Token: ${BINDING_TOKEN}" -H "Authorization: ${VALUE}"
run_test "$DESC_TOKEN_CHECK"          "$URL_TOKEN_CHECK"          -X GET -H "Approov-Token: ${BINDING_TOKEN}" -H "Authorization: ${VALUE}"
run_test "$DESC_TOKEN_BINDING_CHECK"  "$URL_TOKEN_BINDING_CHECK"  -X GET -H "Approov-Token: ${BINDING_TOKEN}" -H "Authorization: ${VALUE}"
echo "=============================="
echo "== 4) Tests WITH three headers: Approov-Token + Authorization + Content-Digest =="

HASH_INPUT="ExampleAuthToken==ContentDigest=="

# Generate token (grep usually not necessary, but you can keep it for safety)
BINDING_TOKEN_THREE_HEADERS="$(
  approov token -setDataHashInToken "${HASH_INPUT}" -genExample api.example.com \
    | grep -oE '[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+' \
    | head -n1
)"

# For debugging:
echo "TOKEN: $BINDING_TOKEN_THREE_HEADERS"

# Note: we pass WITHOUT '==', because the server will append them when combining
curl -i GET 'http://localhost:8002/token-binding-check-with-two-' \
-H "Authorization: ExampleAuthToken==" \
-H "Content-Digest: ContentDigest==" \
-H "Approov-Token: $BINDING_TOKEN_THREE_HEADERS"

echo "== Done =="
#
#  export HASH_INPUT="ExampleAuthToken==ContentDigest=="
#  approov token -setDataHashInToken "$HASH_INPUT" -genExample example.com > .config/approov_token_3_valid
#  curl -H "Authorization: ExampleAuthToken==" -H "Content-Digest: ContentDigest==" -H "approov-token: $(cat .config/approov_token_3_valid)" http://localhost:8080/token-binding-2
