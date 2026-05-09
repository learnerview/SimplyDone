#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
API_KEY="${API_KEY:-}"
ADMIN_KEY="${ADMIN_KEY:-}"

post_job() {
  local body="$1"
  curl -sS -X POST "${BASE_URL}/api/jobs" \
    -H "X-API-KEY: ${API_KEY}" \
    -H "Content-Type: application/json" \
    -d "${body}"
  printf '\n'
}

create_admin_key() {
  curl -sS -X POST "${BASE_URL}/api/admin/keys" \
    -H "X-API-KEY: ${ADMIN_KEY}" \
    -H "Content-Type: application/json" \
    -d '{"label":"Demo Tenant","producer":"demo-producer","admin":false}'
  printf '\n'
}

if [[ -z "${API_KEY}" ]]; then
  echo "API_KEY must be set" >&2
  exit 1
fi

if [[ -n "${ADMIN_KEY}" ]]; then
  create_admin_key
fi

post_job '{
  "jobType":"webhook",
  "idempotencyKey":"demo-webhook-1",
  "priority":"HIGH",
  "execution":{"type":"HTTP","endpoint":"https://example.com/webhook"},
  "payload":{"type":"email","recipient":"demo@example.com"},
  "maxAttempts":3,
  "timeoutSeconds":30
}'

post_job '{
  "jobType":"delayed-report",
  "idempotencyKey":"demo-report-1",
  "priority":"NORMAL",
  "nextRunAt":"2026-05-10T10:00:00Z",
  "execution":{"type":"HTTP","endpoint":"https://example.com/report"},
  "payload":{"type":"report","format":"csv"},
  "maxAttempts":3,
  "timeoutSeconds":30
}'

post_job '{
  "jobType":"failure-demo",
  "idempotencyKey":"demo-failure-1",
  "priority":"LOW",
  "execution":{"type":"HTTP","endpoint":"https://invalid.localhost/demo"},
  "payload":{"type":"failure-demo"},
  "maxAttempts":2,
  "timeoutSeconds":10
}'

echo "Demo data requests submitted."