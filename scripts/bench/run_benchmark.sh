#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

BASE_URL="${BASE_URL:-http://localhost:8080}"
ORDERS="${ORDERS:-5000}"
ATTRS="${ATTRS:-15}"
WARMUP_RUNS="${WARMUP_RUNS:-2}"
MEASURE_RUNS="${MEASURE_RUNS:-3}"
STRATEGIES="${STRATEGIES:-preload multiset sequence-window spliterator-window}"
OUT_ROOT="${OUT_ROOT:-docs/benchmark/runs}"
RUN_LABEL="${RUN_LABEL:-$(date +%Y%m%d-%H%M%S)}"
RUN_DIR="${OUT_ROOT}/${RUN_LABEL}_${ORDERS}orders_${ATTRS}attrs"
SKIP_SEED="${SKIP_SEED:-auto}"  # auto / 0 (force seed) / 1 (skip seed)
METRICS_BASE="${METRICS_BASE:-}" # e.g. http://localhost:8080/actuator

mkdir -p "${RUN_DIR}/artifacts"

# Auto-detect if data already exists
if [[ "${SKIP_SEED}" == "auto" ]]; then
  echo "Checking existing data..."
  SUMMARY=$(curl -sS "${BASE_URL}/admin/summary" 2>/dev/null || echo '{}')
  CURRENT_ORDERS=$(echo "$SUMMARY" | jq -r '.orders // 0' 2>/dev/null || echo '0')

  if [[ "$CURRENT_ORDERS" -eq "$ORDERS" ]]; then
    echo "✓ Data already exists (${CURRENT_ORDERS} orders). Skipping seed."
    SKIP_SEED=1
  else
    echo "Data mismatch (current: ${CURRENT_ORDERS}, expected: ${ORDERS}). Running seed."
    SKIP_SEED=0
  fi
fi

# Execute seed if needed
if [[ "${SKIP_SEED}" -ne 1 ]]; then
  "${SCRIPT_DIR}/seed_benchmark_dataset.sh" "${ORDERS}" "${ATTRS}"
fi

echo "Running benchmarks -> ${RUN_DIR}"
echo "Strategies: ${STRATEGIES}"
echo "Warmup runs: ${WARMUP_RUNS}, Measure runs: ${MEASURE_RUNS}"

call_api() {
  local strategy="$1"
  local label="$2"      # warmup1 / run1 etc

  local outfile="${RUN_DIR}/artifacts/${strategy}-${label}.csv"
  local log="${RUN_DIR}/${strategy}-${label}.log"
  local meta="${RUN_DIR}/${strategy}-${label}.json"

  echo "  -> ${strategy} (${label})"
  curl -sS \
    -w '{"time_total":%{time_total},"size_download":%{size_download},"http_code":%{http_code},"speed_download":%{speed_download}}\n' \
    -o "${outfile}" \
    "${BASE_URL}/api/export/orders/attributes?strategy=${strategy}" \
    | tee "${meta}"

  md5sum "${outfile}" | awk '{print $1}' > "${RUN_DIR}/${strategy}-${label}.md5"

  if [[ -n "${METRICS_BASE}" ]]; then
    curl -sS "${METRICS_BASE}/metrics/http.server.requests?tag=uri:/api/export/orders/attributes&tag=method:GET" \
      > "${RUN_DIR}/${strategy}-${label}-http-metrics.json" || true
    curl -sS "${METRICS_BASE}/metrics/jvm.memory.used" \
      > "${RUN_DIR}/${strategy}-${label}-memory.json" || true
    curl -sS "${METRICS_BASE}/metrics/jvm.gc.pause" \
      > "${RUN_DIR}/${strategy}-${label}-gc.json" || true
  fi
}

for strategy in ${STRATEGIES}; do
  echo "=== ${strategy} ==="

  for i in $(seq 1 "${WARMUP_RUNS}"); do
    call_api "${strategy}" "warmup${i}"
  done

  for i in $(seq 1 "${MEASURE_RUNS}"); do
    call_api "${strategy}" "run${i}"
  done
done

echo "Done. Results stored in ${RUN_DIR}"
