#!/usr/bin/env bash
set -euo pipefail

echo ""
echo "=============================="
echo "🚀 Starting Approov Quickstart Demo"
echo "=============================="
echo ""

sleep 3

# kill anything on 8002
echo " Stopping any process running on port 8002..."
lsof -ti:8002 | xargs kill -9 2>/dev/null || true
sleep 3

# build
echo " Building the Spring Boot project..."
sleep 3

./gradlew build
sleep 3

# start server with env (in background)
echo ""
echo "🟢 Starting server with environment variables from .env..."
set -a  # auto-export all assignments
source .env
nohup ./gradlew bootRun > bootRun.log 2>&1 &
SERVER_PID=$!
set +a  # stop exporting variables
sleep 4

echo ""
echo "⏳ Waiting for server to start on port 8002..."
sleep 4

# call without token (expect 400)
echo ""
echo "🔴 Making request WITH invalid Approov-Token (expect 401 Unauthorized)..."
sleep 4
curl -iX GET http://localhost:8002/ \
  --header 'Approov-Token: eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJleHAiOjQ3MDg2ODMyMDUuODkxOTEyfQ._ZdLOZmK4KXSIpVlhOpHBgboSHHTWer-X6oLqFIDQWI'
sleep 4

# generate token
echo ""
echo "🔐 Generating Approov Token for api.example.com..."
echo "using approov token -genExample api.example.com"
sleep 4
RAW_OUT="$(approov token -genExample api.example.com || true)"
TOKEN="$(printf '%s\n' "$RAW_OUT" | grep -oE '[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+' | head -n1)"
sleep 4

echo ""
echo "✅ Approov token generated successfully:"
echo "$TOKEN"
sleep 3

# call with token (expect 200)
echo ""
echo "🟢 Making request WITH Approov-Token (expect 200 OK)..."
curl -iX GET 'http://localhost:8002/' \
  --header "Approov-Token: ${TOKEN}"
sleep 3

# (optional) stop server when script ends
# kill "$SERVER_PID" 2>/dev/null || true

echo ""
echo "=============================="
echo "✅ Demo finished successfully!"
echo "=============================="
