#!/usr/bin/env bash
# Message Signing (IPK) compatibility tests
# - RFC 9421 (HTTP Message Signatures): Signature-Input / Signature
# - RFC 9530 (Content-Digest): sha-256=:<base64 no padding>:
# Works against Spring, Laravel, Node, ASP.NET Core, etc.
# Requires a local signing helper endpoint: ${BASE_URL}/ipk_message_sign_test

set -euo pipefail

########################################
# ----------- CONFIG ----------------- #
########################################

BASE_URL="${BASE_URL:-http://localhost:8080}"
HDR_NAME="${HDR_NAME:-approov-token}"        # Approov token header name sent by client
SIGN_LABEL_1="install"                       # primary signature label
SIGN_LABEL_2="install2"                      # secondary label (key rotation / multi-sig)
KEYID_1="${KEYID_1:-install}"                # exposed in Signature-Input as keyid
KEYID_2="${KEYID_2:-install-rotated}"        # for rotation tests

# Example EC private key (P-256) in base64 (DER/PKCS8). For tests only.
TEST_EC_PRIV_B64="${TEST_EC_PRIV_B64:-MHcCAQEEIHWZ2Ueq6odQNG+aaYmEbp7C6nujYNGr7nYKK2jqQ2asoAoGCCqGSM49AwEHoUQDQgAEJSm4DMcivAwvhM+KNce2C/X26cj3oGyUwWVUPuNuZHtd2qyVsM+0g7qX73Qh0Of6fn10AApLnl8vRQsvx94fZQ==}"

# Put a valid Approov token here (or inject via env). The server must accept it.
APPROOV_IPK_TOKEN_OK="${APPROOV_IPK_TOKEN_OK:-eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...}"

# Housekeeping
VERBOSE="${VERBOSE:-0}"                      # 1 → print canonical block + inputs
APPROOV_DISABLED="${APPROOV_DISABLED:-${approov_disabled:-false}}"

# Clock defaults
DEFAULT_CREATED=${DEFAULT_CREATED:-1744292750}
DEFAULT_EXPIRES=${DEFAULT_EXPIRES:-1999999999}

########################################
# ----------- HELPERS ---------------- #
########################################

die(){ echo "ERR: $*" >&2; exit 1; }

b64()       { openssl base64 -A; }
b64_nopad() { tr -d '='; }
sha256_bin(){ openssl dgst -binary -sha256; }
sha256_b64(){ sha256_bin | b64; }
sha256_b64_nopad(){ sha256_bin | b64 | b64_nopad; }

# Print canonical block (each line already quoted correctly)
canon_block(){ printf "%s\n" "$@"; }

# Build Signature-Input value
# $1 components string e.g. '"@method" "@target-uri" "'"${HDR_NAME}"'" "content-digest"'
# $2 created int    $3 expires int
# $4 extra_kv (e.g., 'keyid="install"') – optional
make_sig_input(){
  local comps="$1" created="$2" expires="$3" kv="${4:-}"
  local base="(${comps});created=${created};expires=${expires}"
  if [[ -n "$kv" ]]; then printf '%s;%s' "$base" "$kv"; else printf '%s' "$base"; fi
}

# Ask the helper endpoint to sign a base64 canonical string using P-256
sign_with_test_key(){
  local canon_b64="$1"
  curl -sS -H "private-key: ${TEST_EC_PRIV_B64}" -H "msg: ${canon_b64}" \
    "${BASE_URL}/ipk_message_sign_test"
}

# Result expectations: if Approov checks are globally disabled on server,
# negative tests may still return 200.
expect_ok()   { echo 200; }
expect_fail() { [[ "$APPROOV_DISABLED" == true ]] && echo 200 || echo 401; }
expect_bad()  { [[ "$APPROOV_DISABLED" == true ]] && echo 200 || echo 400; }

# Execute a single HTTP case, asserting expected code
run_case(){ # name, expect_code, curl_args...
  local name="$1"; shift
  local expect="$1"; shift
  echo -e "\n*** ${name} ***"
  # shellcheck disable=SC2086
  local code
  code="$(curl -s -o /dev/null -w '%{http_code}' "$@")"
  if [[ "$code" != "$expect" ]]; then
    echo "FAIL: got ${code}, expected ${expect}"
    return 1
  fi
  echo "OK  : ${code}"
}

# Utilities for body/digest
digest_header_for_body(){ # echoes: content-digest header value sha-256=:...:
  local body="$1"
  local np
  np="$(printf "%s" "$body" | sha256_bin | b64 | b64_nopad)"
  printf 'sha-256=:%s:' "$np"
}

########################################
# ----------- TEST CASES ------------- #
########################################

pass=0; fail=0
tally(){ if "$@"; then pass=$((pass+1)); else fail=$((fail+1)); fi; }

# ========== 6.1 Minimal: @method + Approov header ==========
test_61(){
  local CREATED=${DEFAULT_CREATED} EXPIRES=${DEFAULT_EXPIRES}
  local TARGET_URL="${BASE_URL}/token?param1=value1&param2=value2"
  local COMPS=$(printf '"@method" "%s"' "${HDR_NAME}")
  local SIG_INPUT
  SIG_INPUT=$(make_sig_input "${COMPS}" "$CREATED" "$EXPIRES" "keyid=\"${KEYID_1}\"")
  local CANON
  CANON=$(canon_block \
    "\"@method\": GET" \
    "\"${HDR_NAME}\": ${APPROOV_IPK_TOKEN_OK}" \
    "\"@signature-params\": ${SIG_INPUT}")
  [[ "$VERBOSE" == 1 ]] && { echo "--- CANON ---"; echo "$CANON"; echo "Sig-Input: $SIG_INPUT"; }

  local SIG
  SIG="$(printf "%s" "$CANON" | b64 | sign_with_test_key)"
  run_case "MS 6.1 minimal (@method + ${HDR_NAME})" "$(expect_ok)" \
    -H "${HDR_NAME}: ${APPROOV_IPK_TOKEN_OK}" \
    -H "signature: ${SIGN_LABEL_1}=:${SIG}:" \
    -H "signature-input: ${SIGN_LABEL_1}=${SIG_INPUT}" \
    "${TARGET_URL}"
}

# ========== 6.2 @target-uri ==========
test_62(){
  local CREATED=${DEFAULT_CREATED} EXPIRES=${DEFAULT_EXPIRES}
  local TARGET_URL="${BASE_URL}/token?param1=value1&param2=value2"
  local COMPS=$(printf '"@method" "@target-uri" "%s"' "${HDR_NAME}")
  local SIG_INPUT
  SIG_INPUT=$(make_sig_input "${COMPS}" "$CREATED" "$EXPIRES" "keyid=\"${KEYID_1}\"")
  local CANON
  CANON=$(canon_block \
    "\"@method\": GET" \
    "\"@target-uri\": ${TARGET_URL}" \
    "\"${HDR_NAME}\": ${APPROOV_IPK_TOKEN_OK}" \
    "\"@signature-params\": ${SIG_INPUT}")
  [[ "$VERBOSE" == 1 ]] && { echo "--- CANON ---"; echo "$CANON"; }

  local SIG; SIG="$(printf "%s" "$CANON" | b64 | sign_with_test_key)"
  run_case "MS 6.2 +@target-uri" "$(expect_ok)" \
    -H "${HDR_NAME}: ${APPROOV_IPK_TOKEN_OK}" \
    -H "signature: ${SIGN_LABEL_1}=:${SIG}:" \
    -H "signature-input: ${SIGN_LABEL_1}=${SIG_INPUT}" \
    "${TARGET_URL}"
}

# ========== 6.3 POST + Content-Digest (quoted-string, tolerance) ==========
# Non-canonical on purpose: tests server tolerance (still should verify)
test_63(){
  local CREATED=${DEFAULT_CREATED} EXPIRES=${DEFAULT_EXPIRES}
  local TARGET_URL="${BASE_URL}/token?param1=value1&param2=value2"
  local BODY='My test message body'
  local BODY_B64; BODY_B64="$(printf "%s" "$BODY" | sha256_bin | b64)" # with padding
  local COMPS=$(printf '"@method" "@target-uri" "%s" "content-digest"' "${HDR_NAME}")
  local SIG_INPUT
  SIG_INPUT=$(make_sig_input "${COMPS}" "$CREATED" "$EXPIRES" "keyid=\"${KEYID_1}\"")
  local CANON
  CANON=$(canon_block \
    "\"@method\": POST" \
    "\"@target-uri\": ${TARGET_URL}" \
    "\"${HDR_NAME}\": ${APPROOV_IPK_TOKEN_OK}" \
    "\"content-digest\": sha-256=\"${BODY_B64}\"" \
    "\"@signature-params\": ${SIG_INPUT}")
  [[ "$VERBOSE" == 1 ]] && { echo "--- CANON ---"; echo "$CANON"; }

  local SIG; SIG="$(printf "%s" "$CANON" | b64 | sign_with_test_key)"
  run_case "MS 6.3 POST + digest (quoted-string tolerance)" "$(expect_ok)" \
    -X POST \
    -H "${HDR_NAME}: ${APPROOV_IPK_TOKEN_OK}" \
    -H "content-digest: sha-256=\"${BODY_B64}\"" \
    -H "signature: ${SIGN_LABEL_1}=:${SIG}:" \
    -H "signature-input: ${SIGN_LABEL_1}=${SIG_INPUT}" \
    --data "$BODY" \
    "${TARGET_URL}"
}

# ========== 6.4 POST + Content-Digest (byte-seq, canonical) ==========
test_64(){
  local CREATED=${DEFAULT_CREATED} EXPIRES=${DEFAULT_EXPIRES}
  local TARGET_URL="${BASE_URL}/token?param1=value1&param2=value2"
  local BODY='My test message body'
  local DIGEST; DIGEST="$(digest_header_for_body "$BODY")"
  local COMPS=$(printf '"@method" "@target-uri" "%s" "content-digest"' "${HDR_NAME}")
  local SIG_INPUT
  SIG_INPUT=$(make_sig_input "${COMPS}" "$CREATED" "$EXPIRES" "keyid=\"${KEYID_1}\"")
  local CANON
  CANON=$(canon_block \
    "\"@method\": POST" \
    "\"@target-uri\": ${TARGET_URL}" \
    "\"${HDR_NAME}\": ${APPROOV_IPK_TOKEN_OK}" \
    "\"content-digest\": ${DIGEST}" \
    "\"@signature-params\": ${SIG_INPUT}")
  [[ "$VERBOSE" == 1 ]] && { echo "--- CANON ---"; echo "$CANON"; }

  local SIG; SIG="$(printf "%s" "$CANON" | b64 | sign_with_test_key)"
  run_case "MS 6.4 POST + digest (byte-seq canonical)" "$(expect_ok)" \
    -X POST \
    -H "${HDR_NAME}: ${APPROOV_IPK_TOKEN_OK}" \
    -H "content-digest: ${DIGEST}" \
    -H "signature: ${SIGN_LABEL_1}=:${SIG}:" \
    -H "signature-input: ${SIGN_LABEL_1}=${SIG_INPUT}" \
    --data "$BODY" \
    "${TARGET_URL}"
}

# ========== 6.5 NEG: tampered path ==========
test_65(){
  local CREATED=${DEFAULT_CREATED} EXPIRES=${DEFAULT_EXPIRES}
  local GOOD_URL="${BASE_URL}/token?param1=value1&param2=value2"
  local BAD_URL="${BASE_URL}/tokenX?param1=value1&param2=value2"
  local COMPS=$(printf '"@method" "@target-uri" "%s"' "${HDR_NAME}")
  local SIG_INPUT; SIG_INPUT=$(make_sig_input "${COMPS}" "$CREATED" "$EXPIRES" "keyid=\"${KEYID_1}\"")
  local CANON
  CANON=$(canon_block \
    "\"@method\": GET" \
    "\"@target-uri\": ${GOOD_URL}" \
    "\"${HDR_NAME}\": ${APPROOV_IPK_TOKEN_OK}" \
    "\"@signature-params\": ${SIG_INPUT}")
  local SIG; SIG="$(printf "%s" "$CANON" | b64 | sign_with_test_key)"
  run_case "MS 6.5 NEG tampered path" "$(expect_fail)" \
    -H "${HDR_NAME}: ${APPROOV_IPK_TOKEN_OK}" \
    -H "signature: ${SIGN_LABEL_1}=:${SIG}:" \
    -H "signature-input: ${SIGN_LABEL_1}=${SIG_INPUT}" \
    "${BAD_URL}"
}

# ========== 6.6 NEG: future created (beyond skew) ==========
test_66(){
  local now created; now="$(date +%s)"; created=$((now + 3600))
  local TARGET_URL="${BASE_URL}/token?param1=value1&param2=value2"
  local COMPS=$(printf '"@method" "@target-uri" "%s"' "${HDR_NAME}")
  local SIG_INPUT; SIG_INPUT=$(make_sig_input "${COMPS}" "$created" "$DEFAULT_EXPIRES" "keyid=\"${KEYID_1}\"")
  local CANON
  CANON=$(canon_block \
    "\"@method\": GET" \
    "\"@target-uri\": ${TARGET_URL}" \
    "\"${HDR_NAME}\": ${APPROOV_IPK_TOKEN_OK}" \
    "\"@signature-params\": ${SIG_INPUT}")
  local SIG; SIG="$(printf "%s" "$CANON" | b64 | sign_with_test_key)"
  run_case "MS 6.6 NEG future created" "$(expect_fail)" \
    -H "${HDR_NAME}: ${APPROOV_IPK_TOKEN_OK}" \
    -H "signature: ${SIGN_LABEL_1}=:${SIG}:" \
    -H "signature-input: ${SIGN_LABEL_1}=${SIG_INPUT}" \
    "${TARGET_URL}"
}

# ========== 6.7 NEG: digest mismatch ==========
test_67(){
  local CREATED=${DEFAULT_CREATED} EXPIRES=${DEFAULT_EXPIRES}
  local TARGET_URL="${BASE_URL}/token?param1=value1&param2=value2"
  local BODY='My test message body'
  local WRONG; WRONG="$(digest_header_for_body "tampered")"
  local COMPS=$(printf '"@method" "@target-uri" "%s" "content-digest"' "${HDR_NAME}")
  local SIG_INPUT; SIG_INPUT=$(make_sig_input "${COMPS}" "$CREATED" "$EXPIRES" "keyid=\"${KEYID_1}\"")
  local CANON
  CANON=$(canon_block \
    "\"@method\": POST" \
    "\"@target-uri\": ${TARGET_URL}" \
    "\"${HDR_NAME}\": ${APPROOV_IPK_TOKEN_OK}" \
    "\"content-digest\": ${WRONG}" \
    "\"@signature-params\": ${SIG_INPUT}")
  local SIG; SIG="$(printf "%s" "$CANON" | b64 | sign_with_test_key)"
  run_case "MS 6.7 NEG digest mismatch" "$(expect_fail)" \
    -X POST \
    -H "${HDR_NAME}: ${APPROOV_IPK_TOKEN_OK}" \
    -H "content-digest: ${WRONG}" \
    -H "signature: ${SIGN_LABEL_1}=:${SIG}:" \
    -H "signature-input: ${SIGN_LABEL_1}=${SIG_INPUT}" \
    --data "$BODY" \
    "${TARGET_URL}"
}

# ========== 6.8 multi-value header join + case-insensitive header name ==========
test_68(){
  local CREATED=${DEFAULT_CREATED} EXPIRES=${DEFAULT_EXPIRES}
  local TARGET_URL="${BASE_URL}/token?param1=value1&param2=value2"
  local COMPS='"@method" "@target-uri" "x-extra-header"'
  local SIG_INPUT; SIG_INPUT=$(make_sig_input "${COMPS}" "$CREATED" "$EXPIRES" "keyid=\"${KEYID_1}\"")
  local CANON
  CANON=$(canon_block \
    "\"@method\": GET" \
    "\"@target-uri\": ${TARGET_URL}" \
    "\"x-extra-header\": one, two" \
    "\"@signature-params\": ${SIG_INPUT}")
  local SIG; SIG="$(printf "%s" "$CANON" | b64 | sign_with_test_key)"
  run_case "MS 6.8 multi-value header join + casing" "$(expect_ok)" \
    -H "${HDR_NAME}: ${APPROOV_IPK_TOKEN_OK}" \
    -H "X-Extra-Header: one" \
    -H "x-extra-header: two" \
    -H "signature: ${SIGN_LABEL_1}=:${SIG}:" \
    -H "signature-input: ${SIGN_LABEL_1}=${SIG_INPUT}" \
    "${TARGET_URL}"
}

# ========== 6.9 NEG: label mismatch (Signature vs Signature-Input) ==========
test_69(){
  local CREATED=${DEFAULT_CREATED} EXPIRES=${DEFAULT_EXPIRES}
  local TARGET_URL="${BASE_URL}/token?param1=value1&param2=value2"
  local COMPS=$(printf '"@method" "%s"' "${HDR_NAME}")
  local SIG_INPUT; SIG_INPUT=$(make_sig_input "${COMPS}" "$CREATED" "$EXPIRES" "keyid=\"${KEYID_1}\"")
  local CANON
  CANON=$(canon_block \
    "\"@method\": GET" \
    "\"${HDR_NAME}\": ${APPROOV_IPK_TOKEN_OK}" \
    "\"@signature-params\": ${SIG_INPUT}")
  local SIG; SIG="$(printf "%s" "$CANON" | b64 | sign_with_test_key)"
  run_case "MS 6.9 NEG label mismatch" "$(expect_fail)" \
    -H "${HDR_NAME}: ${APPROOV_IPK_TOKEN_OK}" \
    -H "signature: ${SIGN_LABEL_1}=:${SIG}:" \
    -H "signature-input: device=${SIG_INPUT}" \
    "${TARGET_URL}"
}

# ========== 6.10 NEG: unsupported alg value (legacy param) ==========
test_610(){
  local CREATED=${DEFAULT_CREATED} EXPIRES=${DEFAULT_EXPIRES}
  local TARGET_URL="${BASE_URL}/token?param1=value1&param2=value2"
  local COMPS=$(printf '"@method" "@target-uri" "%s"' "${HDR_NAME}")
  # Intentionally include alg that does not match real signature
  local SIG_INPUT
  SIG_INPUT=$(make_sig_input "${COMPS}" "$CREATED" "$EXPIRES" 'alg="rsa-pss-sha256";keyid="legacy"')
  local CANON
  CANON=$(canon_block \
    "\"@method\": GET" \
    "\"@target-uri\": ${TARGET_URL}" \
    "\"${HDR_NAME}\": ${APPROOV_IPK_TOKEN_OK}" \
    "\"@signature-params\": ${SIG_INPUT}")
  local SIG; SIG="$(printf "%s" "$CANON" | b64 | sign_with_test_key)"
  run_case "MS 6.10 NEG alg unsupported" "$(expect_fail)" \
    -H "${HDR_NAME}: ${APPROOV_IPK_TOKEN_OK}" \
    -H "signature: ${SIGN_LABEL_1}=:${SIG}:" \
    -H "signature-input: ${SIGN_LABEL_1}=${SIG_INPUT}" \
    "${TARGET_URL}"
}

# ========== 6.11 @request-target (compat check) ==========
test_611(){
  local CREATED=${DEFAULT_CREATED} EXPIRES=${DEFAULT_EXPIRES}
  local PATH="/token" QS="?param1=value1&param2=value2"
  local TARGET_URL="${BASE_URL}${PATH}${QS}"
  local COMPS=$(printf '"@method" "@request-target" "%s"' "${HDR_NAME}")
  local SIG_INPUT; SIG_INPUT=$(make_sig_input "${COMPS}" "$CREATED" "$EXPIRES" "keyid=\"${KEYID_1}\"")
  local CANON
  CANON=$(canon_block \
    "\"@method\": GET" \
    "\"@request-target\": ${PATH}${QS}" \
    "\"${HDR_NAME}\": ${APPROOV_IPK_TOKEN_OK}" \
    "\"@signature-params\": ${SIG_INPUT}")
  local SIG; SIG="$(printf "%s" "$CANON" | b64 | sign_with_test_key)"
  run_case "MS 6.11 @request-target" "$(expect_ok)" \
    -H "${HDR_NAME}: ${APPROOV_IPK_TOKEN_OK}" \
    -H "signature: ${SIGN_LABEL_1}=:${SIG}:" \
    -H "signature-input: ${SIGN_LABEL_1}=${SIG_INPUT}" \
    "${TARGET_URL}"
}

# ========== 6.12 @query-param multi-value ==========
test_612(){
  local CREATED=${DEFAULT_CREATED} EXPIRES=${DEFAULT_EXPIRES}
  local TARGET_URL="${BASE_URL}/token?param1=value1&param2=value2&param2=value2b"
  local COMPS=$(printf '"@method" "@target-uri" "@query-param";name="param2" "%s"' "${HDR_NAME}")
  local SIG_INPUT; SIG_INPUT=$(make_sig_input "${COMPS}" "$CREATED" "$EXPIRES" "keyid=\"${KEYID_1}\"")
  local CANON
  CANON=$(canon_block \
    "\"@method\": GET" \
    "\"@target-uri\": ${TARGET_URL}" \
    "\"@query-param\";name=\"param2\": value2, value2b" \
    "\"${HDR_NAME}\": ${APPROOV_IPK_TOKEN_OK}" \
    "\"@signature-params\": ${SIG_INPUT}")
  local SIG; SIG="$(printf "%s" "$CANON" | b64 | sign_with_test_key)"
  run_case "MS 6.12 @query-param (multi)" "$(expect_ok)" \
    -H "${HDR_NAME}: ${APPROOV_IPK_TOKEN_OK}" \
    -H "signature: ${SIGN_LABEL_1}=:${SIG}:" \
    -H "signature-input: ${SIGN_LABEL_1}=${SIG_INPUT}" \
    "${TARGET_URL}"
}

# ========== 6.13 NEG: created too old (past skew) ==========
test_613(){
  local old=$(( $(date +%s) - 3600 ))
  local TARGET_URL="${BASE_URL}/token"
  local COMPS=$(printf '"@method" "@target-uri" "%s"' "${HDR_NAME}")
  local SIG_INPUT; SIG_INPUT=$(make_sig_input "${COMPS}" "$old" "$DEFAULT_EXPIRES" "keyid=\"${KEYID_1}\"")
  local CANON; CANON=$(canon_block "\"@method\": GET" "\"@target-uri\": ${TARGET_URL}" "\"${HDR_NAME}\": ${APPROOV_IPK_TOKEN_OK}" "\"@signature-params\": ${SIG_INPUT}")
  local SIG; SIG="$(printf "%s" "$CANON" | b64 | sign_with_test_key)"
  run_case "MS 6.13 NEG created too old" "$(expect_fail)" \
    -H "${HDR_NAME}: ${APPROOV_IPK_TOKEN_OK}" \
    -H "signature: ${SIGN_LABEL_1}=:${SIG}:" \
    -H "signature-input: ${SIGN_LABEL_1}=${SIG_INPUT}" \
    "${TARGET_URL}"
}

# ========== 6.14 NEG: expires in the past ==========
test_614(){
  local CREATED=${DEFAULT_CREATED} EXPIRES=$(( $(date +%s) - 10 ))
  local TARGET_URL="${BASE_URL}/token"
  local COMPS=$(printf '"@method" "@target-uri" "%s"' "${HDR_NAME}")
  local SIG_INPUT; SIG_INPUT=$(make_sig_input "${COMPS}" "$CREATED" "$EXPIRES" "keyid=\"${KEYID_1}\"")
  local CANON; CANON=$(canon_block "\"@method\": GET" "\"@target-uri\": ${TARGET_URL}" "\"${HDR_NAME}\": ${APPROOV_IPK_TOKEN_OK}" "\"@signature-params\": ${SIG_INPUT}")
  local SIG; SIG="$(printf "%s" "$CANON" | b64 | sign_with_test_key)"
  run_case "MS 6.14 NEG expires past" "$(expect_fail)" \
    -H "${HDR_NAME}: ${APPROOV_IPK_TOKEN_OK}" \
    -H "signature: ${SIGN_LABEL_1}=:${SIG}:" \
    -H "signature-input: ${SIGN_LABEL_1}=${SIG_INPUT}" \
    "${TARGET_URL}"
}

# ========== 6.15 NEG: missing required component (POST without Content-Digest) ==========
test_615(){
  local CREATED=${DEFAULT_CREATED} EXPIRES=${DEFAULT_EXPIRES}
  local TARGET_URL="${BASE_URL}/token"
  local BODY='payload'
  local COMPS=$(printf '"@method" "@target-uri" "%s"' "${HDR_NAME}") # no content-digest
  local SIG_INPUT; SIG_INPUT=$(make_sig_input "${COMPS}" "$CREATED" "$EXPIRES" "keyid=\"${KEYID_1}\"")
  local CANON; CANON=$(canon_block "\"@method\": POST" "\"@target-uri\": ${TARGET_URL}" "\"${HDR_NAME}\": ${APPROOV_IPK_TOKEN_OK}" "\"@signature-params\": ${SIG_INPUT}")
  local SIG; SIG="$(printf "%s" "$CANON" | b64 | sign_with_test_key)"
  # 400 is correct (bad request); some stacks may map to 401; we accept expect_bad()
  run_case "MS 6.15 NEG POST missing Content-Digest" "$(expect_bad)" \
    -X POST \
    -H "${HDR_NAME}: ${APPROOV_IPK_TOKEN_OK}" \
    -H "signature: ${SIGN_LABEL_1}=:${SIG}:" \
    -H "signature-input: ${SIGN_LABEL_1}=${SIG_INPUT}" \
    --data "$BODY" \
    "${TARGET_URL}"
}

# ========== 6.16 raw query reorder (should still fail if canonical used raw order) ==========
test_616(){
  local CREATED=${DEFAULT_CREATED} EXPIRES=${DEFAULT_EXPIRES}
  local CANON_URL="${BASE_URL}/token?b=2&a=1"
  local SENT_URL="${BASE_URL}/token?a=1&b=2"
  local COMPS=$(printf '"@method" "@target-uri" "%s"' "${HDR_NAME}")
  local SIG_INPUT; SIG_INPUT=$(make_sig_input "${COMPS}" "$CREATED" "$EXPIRES" "keyid=\"${KEYID_1}\"")
  local CANON; CANON=$(canon_block "\"@method\": GET" "\"@target-uri\": ${CANON_URL}" "\"${HDR_NAME}\": ${APPROOV_IPK_TOKEN_OK}" "\"@signature-params\": ${SIG_INPUT}")
  local SIG; SIG="$(printf "%s" "$CANON" | b64 | sign_with_test_key)"
  run_case "MS 6.16 NEG raw query reorder" "$(expect_fail)" \
    -H "${HDR_NAME}: ${APPROOV_IPK_TOKEN_OK}" \
    -H "signature: ${SIGN_LABEL_1}=:${SIG}:" \
    -H "signature-input: ${SIGN_LABEL_1}=${SIG_INPUT}" \
    "${SENT_URL}"
}

# ========== 6.17 percent-encoding in path/query (must use raw form) ==========
test_617(){
  local CREATED=${DEFAULT_CREATED} EXPIRES=${DEFAULT_EXPIRES}
  local RAW_PATH="/tok%65n" RAW_QS="?par%61m=value%32"
  local TARGET_URL="${BASE_URL}${RAW_PATH}${RAW_QS}"
  local COMPS=$(printf '"@method" "@request-target" "%s"' "${HDR_NAME}")
  local SIG_INPUT; SIG_INPUT=$(make_sig_input "${COMPS}" "$CREATED" "$EXPIRES" "keyid=\"${KEYID_1}\"")
  local CANON; CANON=$(canon_block "\"@method\": GET" "\"@request-target\": ${RAW_PATH}${RAW_QS}" "\"${HDR_NAME}\": ${APPROOV_IPK_TOKEN_OK}" "\"@signature-params\": ${SIG_INPUT}")
  local SIG; SIG="$(printf "%s" "$CANON" | b64 | sign_with_test_key)"
  run_case "MS 6.17 percent-encoding preserved" "$(expect_ok)" \
    -H "${HDR_NAME}: ${APPROOV_IPK_TOKEN_OK}" \
    -H "signature: ${SIGN_LABEL_1}=:${SIG}:" \
    -H "signature-input: ${SIGN_LABEL_1}=${SIG_INPUT}" \
    "${TARGET_URL}"
}

# ========== 6.18 multi-sig (key rotation): two labels ==========
test_618(){
  local CREATED=${DEFAULT_CREATED} EXPIRES=${DEFAULT_EXPIRES}
  local TARGET_URL="${BASE_URL}/token?x=y"
  local COMPS=$(printf '"@method" "@target-uri" "%s"' "${HDR_NAME}")

  local SI1; SI1=$(make_sig_input "${COMPS}" "$CREATED" "$EXPIRES" "keyid=\"${KEYID_1}\"")
  local CANON1; CANON1=$(canon_block "\"@method\": GET" "\"@target-uri\": ${TARGET_URL}" "\"${HDR_NAME}\": ${APPROOV_IPK_TOKEN_OK}" "\"@signature-params\": ${SI1}")
  local SIG1; SIG1="$(printf "%s" "$CANON1" | b64 | sign_with_test_key)"

  local SI2; SI2=$(make_sig_input "${COMPS}" "$CREATED" "$EXPIRES" "keyid=\"${KEYID_2}\"")
  local CANON2; CANON2=$(canon_block "\"@method\": GET" "\"@target-uri\": ${TARGET_URL}" "\"${HDR_NAME}\": ${APPROOV_IPK_TOKEN_OK}" "\"@signature-params\": ${SI2}")
  local SIG2; SIG2="$(printf "%s" "$CANON2" | b64 | sign_with_test_key)"

  run_case "MS 6.18 multi-sig (rotation)" "$(expect_ok)" \
    -H "${HDR_NAME}: ${APPROOV_IPK_TOKEN_OK}" \
    -H "signature: ${SIGN_LABEL_1}=:${SIG1}:, ${SIGN_LABEL_2}=:${SIG2}:" \
    -H "signature-input: ${SIGN_LABEL_1}=${SI1}, ${SIGN_LABEL_2}=${SI2}" \
    "${TARGET_URL}"
}

# ========== 6.19 NEG: unknown component in Signature-Input ==========
test_619(){
  local CREATED=${DEFAULT_CREATED} EXPIRES=${DEFAULT_EXPIRES}
  local TARGET_URL="${BASE_URL}/token"
  local COMPS=$(printf '"@method" "@target-uri" "@nonexistent" "%s"' "${HDR_NAME}")
  local SI; SI=$(make_sig_input "${COMPS}" "$CREATED" "$EXPIRES" "keyid=\"${KEYID_1}\"")
  local CANON; CANON=$(canon_block "\"@method\": GET" "\"@target-uri\": ${TARGET_URL}" "\"@nonexistent\": ???" "\"${HDR_NAME}\": ${APPROOV_IPK_TOKEN_OK}" "\"@signature-params\": ${SI}")
  local SIG; SIG="$(printf "%s" "$CANON" | b64 | sign_with_test_key)"
  run_case "MS 6.19 NEG unknown component" "$(expect_bad)" \
    -H "${HDR_NAME}: ${APPROOV_IPK_TOKEN_OK}" \
    -H "signature: ${SIGN_LABEL_1}=:${SIG}:" \
    -H "signature-input: ${SIGN_LABEL_1}=${SI}" \
    "${TARGET_URL}"
}

# ========== 6.20 NEG: malformed Signature format ==========
test_620(){
  local CREATED=${DEFAULT_CREATED} EXPIRES=${DEFAULT_EXPIRES}
  local TARGET_URL="${BASE_URL}/token"
  local COMPS=$(printf '"@method" "@target-uri" "%s"' "${HDR_NAME}")
  local SI; SI=$(make_sig_input "${COMPS}" "$CREATED" "$EXPIRES" "keyid=\"${KEYID_1}\"")
  local CANON; CANON=$(canon_block "\"@method\": GET" "\"@target-uri\": ${TARGET_URL}" "\"${HDR_NAME}\": ${APPROOV_IPK_TOKEN_OK}" "\"@signature-params\": ${SI}")
  local SIG; SIG="$(printf "%s" "$CANON" | b64 | sign_with_test_key)"
  # Missing colons around base64 → malformed
  run_case "MS 6.20 NEG malformed Signature" "$(expect_bad)" \
    -H "${HDR_NAME}: ${APPROOV_IPK_TOKEN_OK}" \
    -H "signature: ${SIGN_LABEL_1}=${SIG}" \
    -H "signature-input: ${SIGN_LABEL_1}=${SI}" \
    "${TARGET_URL}"
}

########################################
# -------- RUN ALL & SUMMARY --------- #
########################################

########################################
# -------------- 7. EXTRA -------------#
#  Additional logic/hardening checks   #
########################################

# 7.1 Anti-replay: reuse the exact same signature
test_71_replay_protection(){
  local TARGET_URL="${BASE_URL}/token"
  local BODY='Replay test payload'
  local DIGEST; DIGEST="$(digest_header_for_body "$BODY")"
  local CREATED=${DEFAULT_CREATED} EXPIRES=${DEFAULT_EXPIRES}
  local COMPS; COMPS=$(printf '"@method" "@target-uri" "%s" "content-digest"' "${HDR_NAME}")
  local SI; SI=$(make_sig_input "${COMPS}" "$CREATED" "$EXPIRES" "keyid=\"${KEYID_1}\"")
  local CANON; CANON=$(canon_block "\"@method\": POST" "\"@target-uri\": ${TARGET_URL}" "\"${HDR_NAME}\": ${APPROOV_IPK_TOKEN_OK}" "\"content-digest\": ${DIGEST}" "\"@signature-params\": ${SI}")
  local SIG; SIG="$(printf "%s" "$CANON" | b64 | sign_with_test_key)"

  # first request should pass
  run_case "MS 7.1 replay #1 (should pass)" "$(expect_ok)" \
    -X POST -H "${HDR_NAME}: ${APPROOV_IPK_TOKEN_OK}" \
    -H "content-digest: ${DIGEST}" \
    -H "signature: ${SIGN_LABEL_1}=:${SIG}:" \
    -H "signature-input: ${SIGN_LABEL_1}=${SI}" \
    --data "$BODY" \
    "${TARGET_URL}"

  # reusing the exact same signature should fail if the server enforces anti-replay
  run_case "MS 7.1 replay #2 (should fail)" "$(expect_fail)" \
    -X POST -H "${HDR_NAME}: ${APPROOV_IPK_TOKEN_OK}" \
    -H "content-digest: ${DIGEST}" \
    -H "signature: ${SIGN_LABEL_1}=:${SIG}:" \
    -H "signature-input: ${SIGN_LABEL_1}=${SI}" \
    --data "$BODY" \
    "${TARGET_URL}"
}

# 7.2 Client vs server canonical comparison (requires server to return X-Server-Canonical)
test_72_canonical_match(){
  local TARGET_URL="${BASE_URL}/token?param=1"
  local COMPS; COMPS=$(printf '"@method" "@target-uri" "%s"' "${HDR_NAME}")
  local SI; SI=$(make_sig_input "${COMPS}" "$DEFAULT_CREATED" "$DEFAULT_EXPIRES" "keyid=\"${KEYID_1}\"")
  local LOCAL; LOCAL=$(canon_block "\"@method\": GET" "\"@target-uri\": ${TARGET_URL}" "\"${HDR_NAME}\": ${APPROOV_IPK_TOKEN_OK}" "\"@signature-params\": ${SI}")
  local SIG; SIG="$(printf "%s" "$LOCAL" | b64 | sign_with_test_key)"

  local RESP; RESP="$(curl -s -D - -o /dev/null \
    -H "${HDR_NAME}: ${APPROOV_IPK_TOKEN_OK}" \
    -H "signature: ${SIGN_LABEL_1}=:${SIG}:" \
    -H "signature-input: ${SIGN_LABEL_1}=${SI}" \
    "${TARGET_URL}")"

  local SERVER_CANON
  SERVER_CANON="$(echo "$RESP" | awk -F': ' 'tolower($1)=="x-server-canonical"{sub(/\r$/, "", $2); print $2}')"

  if [[ -z "$SERVER_CANON" ]]; then
    echo "[WARN] 7.2: No X-Server-Canonical header (skipping compare)"
    return 0
  fi
  if [[ "$SERVER_CANON" == "$LOCAL" ]]; then
    echo "OK  : canonical match"
  else
    echo "FAIL: canonical mismatch"; echo "Local : $LOCAL"; echo "Server: $SERVER_CANON"; return 1
  fi
}

# 7.3 Invalid JWT claims (aud/iss/exp) — expect rejection
test_73_invalid_jwt_claims(){
  local BAD_JWT="${BAD_JWT:-eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhdWQiOiJ3cm9uZyIsImlzcyI6Indyb25nIiwiZXhwIjoxNzAwMDAwMDAwfQ.X}"
  local TARGET_URL="${BASE_URL}/token"
  run_case "MS 7.3 invalid JWT claims" "$(expect_fail)" \
    -H "${HDR_NAME}: ${BAD_JWT}" \
    "${TARGET_URL}"
}

# 7.4 Content-Digest whitespace/folding tolerance
test_74_header_whitespace(){
  local TARGET_URL="${BASE_URL}/token"
  local BODY='folded payload'
  local DIGEST_RAW; DIGEST_RAW="$(printf "%s" "$BODY" | sha256_bin | b64 | b64_nopad)"
  local DIGEST_FOLDED=$'sha-256=:\n '"$DIGEST_RAW"$':'   # simulate folding
  local COMPS; COMPS=$(printf '"@method" "@target-uri" "%s" "content-digest"' "${HDR_NAME}")
  local SI; SI=$(make_sig_input "${COMPS}" "$DEFAULT_CREATED" "$DEFAULT_EXPIRES" "keyid=\"${KEYID_1}\"")
  local CANON; CANON=$(canon_block "\"@method\": POST" "\"@target-uri\": ${TARGET_URL}" "\"${HDR_NAME}\": ${APPROOV_IPK_TOKEN_OK}" "\"content-digest\": sha-256=:${DIGEST_RAW}:" "\"@signature-params\": ${SI}")
  local SIG; SIG="$(printf "%s" "$CANON" | b64 | sign_with_test_key)"

  run_case "MS 7.4 header folding tolerance" "$(expect_ok)" \
    -X POST \
    -H "${HDR_NAME}: ${APPROOV_IPK_TOKEN_OK}" \
    -H $'content-digest: '"${DIGEST_FOLDED}" \
    -H "signature: ${SIGN_LABEL_1}=:${SIG}:" \
    -H "signature-input: ${SIGN_LABEL_1}=${SI}" \
    --data "$BODY" \
    "${TARGET_URL}"
}

# 7.5 Header order/casing invariance
test_75_header_order_invariance(){
  local TARGET_URL="${BASE_URL}/token?z=9&y=8"
  local COMPS='"@method" "@target-uri" "x-foo" "x-bar"'
  local SI; SI=$(make_sig_input "${COMPS}" "$DEFAULT_CREATED" "$DEFAULT_EXPIRES" "keyid=\"${KEYID_1}\"")
  local CANON; CANON=$(canon_block "\"@method\": GET" "\"@target-uri\": ${TARGET_URL}" "\"x-foo\": 1" "\"x-bar\": 2" "\"@signature-params\": ${SI}")
  local SIG; SIG="$(printf "%s" "$CANON" | b64 | sign_with_test_key)"

  run_case "MS 7.5 header order invariance" "$(expect_ok)" \
    -H "${HDR_NAME}: ${APPROOV_IPK_TOKEN_OK}" \
    -H "X-Bar: 2" \
    -H "x-foo: 1" \
    -H "signature: ${SIGN_LABEL_1}=:${SIG}:" \
    -H "signature-input: ${SIGN_LABEL_1}=${SI}" \
    "${TARGET_URL}"
}

main(){
  local tests=(
    test_61 test_62 test_63 test_64
    test_65 test_66 test_67 test_68
    test_69 test_610 test_611 test_612
    test_613 test_614 test_615 test_616
    test_617 test_618 test_619 test_620

    # 7 Section 7
    test_71_replay_protection
    test_72_canonical_match
    test_73_invalid_jwt_claims
    test_74_header_whitespace
    test_75_header_order_invariance
  )
  for t in "${tests[@]}"; do
    if "$t"; then tally true; else tally false; fi
  done
  echo -e "\n=== SUMMARY ==="
  echo "Passed: $pass"
  echo "Failed: $fail"
  [[ "$fail" -eq 0 ]]
}

main
