#!/usr/bin/env bash
set -euo pipefail  # fail on error, undefined variable, or pipe failure

# --- Config ---
BASE_URL="http://localhost:8002"
URL_ROOT="${BASE_URL}"
URL_TOKEN_CHECK="${BASE_URL}/token-check"
URL_TOKEN_BINDING_CHECK="${BASE_URL}/token-binding-check"
URL_TOKEN_BINDING_CHECK_TWO_VALUE="${BASE_URL}/token-binding-check-with-two-values"

# Endpoint descriptions (for readable output)
DESC_ROOT="endpoint /"
DESC_TOKEN_CHECK="endpoint /token-check"
DESC_TOKEN_BINDING_CHECK="endpoint /token-binding-check"
DESC_TOKEN_BINDING_CHECK_TWO_VALUE="endpoint /token-binding-check-with-two-values"

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

  # Capture body to a temp and status separately
  local tmp_body
  tmp_body="$(mktemp)"

  local status
  status="$(curl -sS -o "$tmp_body" -w "%{http_code}" "$url" "$@")"

    # Print body only if it's meaningful (not empty, {}, or [])
    if [ -s "$tmp_body" ]; then
      local body
      body="$(cat "$tmp_body" | tr -d '\n' | tr -d '[:space:]')"  # remove spaces/newlines
      if [[ "$body" != "{}" && "$body" != "[]" && -n "$body" ]]; then
        cat "$tmp_body"
        echo
      fi
    fi


  echo "HTTP Status: $status"
  echo

  rm -f "$tmp_body"
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

echo "==============================================="
echo "======== 1) Tests WITHOUT any header =========="
echo "==============================================="
echo "====== curl -X GET 'http:/localhost:8002 ======"
echo "==============================================="
run_test "$DESC_ROOT"                 "$URL_ROOT"                 -X GET
run_test "$DESC_TOKEN_CHECK"          "$URL_TOKEN_CHECK"          -X GET
run_test "$DESC_TOKEN_BINDING_CHECK"  "$URL_TOKEN_BINDING_CHECK"  -X GET


sleep 2

TOKEN="$(
  approov token -genExample api.example.com \
    | grep -oE '[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+' \
    | head -n1
)"

# For debugging:
# echo "TOKEN=${TOKEN}"

echo "============================================================"
echo "========= 2) Tests WITH one header: Approov-Token =========="
echo "============================================================"
echo "=== curl -X GET 'http:/localhost:8002 -H 'Approov-Token' ==="
echo "============================================================"

run_test "$DESC_ROOT"                 "$URL_ROOT"                 -X GET -H "Approov-Token: ${TOKEN}"
run_test "$DESC_TOKEN_CHECK"          "$URL_TOKEN_CHECK"          -X GET -H "Approov-Token: ${TOKEN}"
run_test "$DESC_TOKEN_BINDING_CHECK"  "$URL_TOKEN_BINDING_CHECK"  -X GET -H "Approov-Token: ${TOKEN}"
sleep 5

VALUE="Kmilej"  # value of the bound header
BINDING_TOKEN="$(
  approov token -setDataHashInToken "${VALUE}" -genExample api.example.com \
    | grep -oE '[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+' \
    | head -n1
)"
WRONG_AUTHORIZATION_HEADER="WrongValue"  # wrong value for negative test

# For debugging:
#echo "BindingToken=${BINDING_TOKEN}"

echo "=================================================================================="
echo "============ 3) Tests WITH two headers: Approov-Token + Authorization ============"
echo "=================================================================================="
echo "=== 'curl -iX GET 'http:/localhost:8002/ -H 'Approov-Token' -H 'Authorization'' =="
echo "=================================================================================="

run_test "$DESC_ROOT"                 "$URL_ROOT"                 -X GET -H "Approov-Token: ${BINDING_TOKEN}" -H "Authorization: ${VALUE}"
run_test "$DESC_TOKEN_CHECK"          "$URL_TOKEN_CHECK"          -X GET -H "Approov-Token: ${BINDING_TOKEN}" -H "Authorization: ${VALUE}"
run_test "$DESC_TOKEN_BINDING_CHECK"  "$URL_TOKEN_BINDING_CHECK"  -X GET -H "Approov-Token: ${BINDING_TOKEN}" -H "Authorization: ${VALUE}"
run_test "$DESC_TOKEN_BINDING_CHECK (invalid authorization header )"  "$URL_TOKEN_BINDING_CHECK"  -X GET -H "Approov-Token: ${BINDING_TOKEN}" -H "Authorization: ${WRONG_AUTHORIZATION_HEADER}"

sleep 4
echo "======================================================================================================"
echo "=========== 4) Tests WITH three headers: Approov-Token + Authorization + Content-Digest =============="
echo "======================================================================================================"
echo "=== curl -iX GET 'http:/localhost:8002/ -H 'Approov-Token' -H 'Authorization' -H 'Content-Digest' ==="
echo "======================================================================================================"

HASH_INPUT="ExampleAuthToken==ContentDigest=="

# Generate token (grep usually not necessary, but you can keep it for safety)
BINDING_TOKEN_THREE_HEADERS="$(
  approov token -setDataHashInToken "${HASH_INPUT}" -genExample api.example.com \
    | grep -oE '[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+' \
    | head -n1
)"

# For debugging:
# echo "TOKEN: $BINDING_TOKEN_THREE_HEADERS"

run_test "$DESC_ROOT"                            "$URL_ROOT" -X GET -H 'Authorization: ExampleAuthToken==' -H 'Content-Digest: ContentDigest==' -H "Approov-Token: $BINDING_TOKEN_THREE_HEADERS"
run_test "$DESC_TOKEN_CHECK"                     "$URL_TOKEN_CHECK" -X GET -H 'Authorization: ExampleAuthToken==' -H 'Content-Digest: ContentDigest==' -H "Approov-Token: $BINDING_TOKEN_THREE_HEADERS"
run_test "$DESC_TOKEN_BINDING_CHECK"             "$URL_TOKEN_BINDING_CHECK" -X GET -H 'Authorization: ExampleAuthToken==' -H 'Content-Digest: ContentDigest==' -H "Approov-Token: $BINDING_TOKEN_THREE_HEADERS"
run_test "$DESC_TOKEN_BINDING_CHECK_TWO_VALUE"   "$URL_TOKEN_BINDING_CHECK_TWO_VALUE" -X GET -H 'Authorization: ExampleAuthToken==' -H 'Content-Digest: ContentDigest==' -H "Approov-Token: $BINDING_TOKEN_THREE_HEADERS"


echo "== Done =="
