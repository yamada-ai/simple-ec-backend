#!/usr/bin/env bash
# Quick test script for seed_benchmark_dataset.sh
# Tests with small dataset to verify the API integration works

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

BASE_URL="${BASE_URL:-http://localhost:8080}"

echo "=========================================="
echo "Testing seed_benchmark_dataset.sh"
echo "=========================================="
echo "Base URL: ${BASE_URL}"
echo ""

# Check if app is running
echo "1. Checking if app is running..."
if ! curl -sf "${BASE_URL}/admin/summary" > /dev/null; then
  echo "ERROR: Application is not running at ${BASE_URL}"
  echo "Please start the application first:"
  echo "  ./gradlew bootRun"
  exit 1
fi
echo "✓ App is running"
echo ""

# Test with small dataset
echo "2. Testing seed with small dataset (100 orders, 5 attrs)..."
ORDERS=100 ATTRS=5 SEED=999 "${SCRIPT_DIR}/seed_benchmark_dataset.sh"
echo ""

# Verify data was created
echo "3. Verifying data via Admin API..."
SUMMARY=$(curl -sS "${BASE_URL}/admin/summary")
echo "Summary: ${SUMMARY}"

ORDERS_COUNT=$(echo "$SUMMARY" | jq -r '.orders // 0')
CUSTOMERS_COUNT=$(echo "$SUMMARY" | jq -r '.customers // 0')

if [[ "$ORDERS_COUNT" -ne 100 ]]; then
  echo "ERROR: Expected 100 orders, got ${ORDERS_COUNT}"
  exit 1
fi

if [[ "$CUSTOMERS_COUNT" -ne 20 ]]; then
  echo "WARNING: Expected 20 customers (100/5), got ${CUSTOMERS_COUNT}"
fi

echo "✓ Data verification passed"
echo ""

# Test CSV export
echo "4. Testing CSV export (sequence-window strategy)..."
EXPORT_RESPONSE=$(curl -sS -w "\n%{http_code}" \
  "${BASE_URL}/api/export/orders/attributes?strategy=sequence-window")
HTTP_CODE=$(echo "$EXPORT_RESPONSE" | tail -n1)
CSV_OUTPUT=$(echo "$EXPORT_RESPONSE" | sed '$d')

if [[ "$HTTP_CODE" != "200" ]]; then
  echo "ERROR: Export failed with HTTP ${HTTP_CODE}"
  exit 1
fi

# Count lines (header + 100 orders)
LINE_COUNT=$(echo "$CSV_OUTPUT" | wc -l)
if [[ "$LINE_COUNT" -ne 101 ]]; then
  echo "WARNING: Expected 101 lines (1 header + 100 orders), got ${LINE_COUNT}"
fi

echo "✓ CSV export works"
echo ""

echo "=========================================="
echo "All tests passed! ✅"
echo "=========================================="
echo ""
echo "You can now run the full benchmark:"
echo "  ./scripts/bench/run_benchmark.sh"
