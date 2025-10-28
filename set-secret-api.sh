#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   ./set-secret-api.sh           # writes to ./.env
#   ./set-secret-api.sh path/.env # writes to a specific file
ENV_FILE="${1:-.env}"

# Ensure the .env file exists
# If .env does not exist, copy from .env.example
if [[ ! -f "$ENV_FILE" ]]; then
  if [[ -f ".env.example" ]]; then
    cp .env.example "$ENV_FILE"
    echo " Created ${ENV_FILE} from .env.example"
  else
    echo "⚠  No ${ENV_FILE} or .env.example found. Creating an empty ${ENV_FILE}."
    touch "$ENV_FILE"
  fi
fi


# Run the Approov CLI and capture only the Base64 secret (ignore the "note" line)
SECRET="$(
  approov secret -get base64 2>/dev/null \
  | grep -Eo '^[A-Za-z0-9+/=]{16,}$' \
  | head -n1
)"

# If no valid Base64 secret was found, exit with error
if [[ -z "$SECRET" ]]; then
  echo " ERROR: Approov CLI did not return a Base64 secret (only note or nothing)." >&2
  echo " Run 'approov whoami' to verify your login/profile before retrying."
  exit 1
fi


# Escape quotes (just in case)
SECRET_ESCAPED="${SECRET//\"/\\\"}"

# If key exists (even with leading spaces), replace it; else append
if grep -qE '^[[:space:]]*APPROOV_BASE64_SECRET=' "$ENV_FILE"; then
  awk -v v="$SECRET_ESCAPED" '
    BEGIN{re="^[[:space:]]*APPROOV_BASE64_SECRET="}
    $0 ~ re {$0="APPROOV_BASE64_SECRET=\"" v "\""}
    {print}
  ' "$ENV_FILE" > "${ENV_FILE}.tmp" && mv "${ENV_FILE}.tmp" "$ENV_FILE"
else
  printf 'APPROOV_BASE64_SECRET="%s"\n' "$SECRET_ESCAPED" >> "$ENV_FILE"
fi

echo " APPROOV_BASE64_SECRET updated in ${ENV_FILE}"

YES YES | approov api -add api.example.com >/dev/null
