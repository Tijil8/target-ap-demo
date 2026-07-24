#!/usr/bin/env bash
# End-to-end walkthrough. Run after: docker compose up -d && ./gradlew bootRun
set -euo pipefail
BASE=${BASE:-http://localhost:8080}

say() { printf "\n\033[1;36m== %s ==\033[0m\n" "$1"; }

say "1. Happy path — approved vendor, small amount → settles via Kafka"
OK=$(curl -s -X POST "$BASE/invoices" -H 'content-type: application/json' \
  -d '{"vendorId":"VENDOR-ACME","amount":250.00,"currency":"USD"}')
echo "$OK"
PID_OK=$(echo "$OK" | sed -E 's/.*"paymentId":"([^"]+)".*/\1/')
sleep 1
curl -s "$BASE/invoices/$PID_OK"; echo

say "2. Failure path — unapproved vendor → payments.failed"
BAD=$(curl -s -X POST "$BASE/invoices" -H 'content-type: application/json' \
  -d '{"vendorId":"VENDOR-SHADY","amount":900.00,"currency":"USD"}')
echo "$BAD"
PID_BAD=$(echo "$BAD" | sed -E 's/.*"paymentId":"([^"]+)".*/\1/')
sleep 1
curl -s "$BASE/invoices/$PID_BAD"; echo

say "3. LLM incident triage — explain the failure in plain English"
TRIAGE=$(curl -s "$BASE/incidents/$PID_BAD/explain")
echo "$TRIAGE"; echo

say "done"
# Only nudge toward the real model when we're running the offline mock.
if echo "$TRIAGE" | grep -q '"analyzedBy":"mock"'; then
  echo "Tip: restart with --spring.profiles.active=claude (and ANTHROPIC_API_KEY set) to use the real Claude analyzer."
fi
