#!/bin/bash
# SimplyDone API Verification Script

BASE_URL="http://localhost:8080/api"

echo "--- 1. Health & System Checks ---"
curl -s "$BASE_URL/jobs/health" | jq .
curl -s "$BASE_URL/admin/stats" | jq .
curl -s "$BASE_URL/plugin/health/detailed" | jq .

echo -e "\n--- 2. Job Submission (EMAIL_SEND) ---"
curl -s -X POST "$BASE_URL/jobs" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "test-user",
    "jobType": "EMAIL_SEND",
    "priority": "HIGH",
    "parameters": {
      "to": "test@example.com",
      "subject": "Deployment Test",
      "body": "System verification active."
    }
  }' | jq .

echo -e "\n--- 3. File List (Asset Vault) ---"
curl -s "$BASE_URL/files" | jq .

echo -e "\n--- 4. Dead Letter Queue ---"
curl -s "$BASE_URL/admin/dead-letter-queue" | jq .

echo -e "\n--- Verification Complete ---"
