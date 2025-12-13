#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

ORDERS="${ORDERS:-${1:-5000}}"
ATTRS="${ATTRS:-${2:-15}}"
SEED="${SEED:-${3:-42}}"

BASE_URL="${BASE_URL:-http://localhost:8080}"

# Calculate customers (roughly 1/5 of orders, at least 1)
CUSTOMERS=$(( ORDERS / 5 ))
CUSTOMERS=$(( CUSTOMERS > 0 ? CUSTOMERS : 1 ))

echo "=========================================="
echo "Seeding benchmark dataset via Admin API"
echo "=========================================="
echo "  Base URL: ${BASE_URL}"
echo "  Customers: ${CUSTOMERS}"
echo "  Orders: ${ORDERS}"
echo "  Attributes: ${ATTRS}"
echo "  Seed: ${SEED}"
echo "=========================================="

# First, truncate existing data
echo "Truncating existing data..."
TRUNCATE_RESPONSE=$(curl -sS -X DELETE "${BASE_URL}/admin/truncate" -w "\n%{http_code}")
HTTP_CODE=$(echo "$TRUNCATE_RESPONSE" | tail -n1)
RESPONSE_BODY=$(echo "$TRUNCATE_RESPONSE" | sed '$d')

if [[ "$HTTP_CODE" != "200" ]]; then
  echo "ERROR: Truncate failed with HTTP ${HTTP_CODE}"
  echo "$RESPONSE_BODY"
  exit 1
fi

echo "✓ Truncate completed"

# Seed new data via Admin API
echo "Seeding data..."
SEED_URL="${BASE_URL}/admin/seed?customers=${CUSTOMERS}&orders=${ORDERS}&attrs=${ATTRS}&seed=${SEED}"
SEED_RESPONSE=$(curl -sS -X POST "$SEED_URL" -w "\n%{http_code}")
HTTP_CODE=$(echo "$SEED_RESPONSE" | tail -n1)
RESPONSE_BODY=$(echo "$SEED_RESPONSE" | sed '$d')

if [[ "$HTTP_CODE" != "200" ]]; then
  echo "ERROR: Seed failed with HTTP ${HTTP_CODE}"
  echo "$RESPONSE_BODY"
  exit 1
fi

echo "✓ Seed completed"
echo ""
echo "Results:"
echo "$RESPONSE_BODY" | jq '.' 2>/dev/null || echo "$RESPONSE_BODY"
echo ""
echo "Done."
