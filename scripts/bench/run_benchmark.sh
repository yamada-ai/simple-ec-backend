#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Configuration
BASE_URL="${BASE_URL:-http://localhost:8080}"
ORDERS="${ORDERS:-5000}"
ATTRS="${ATTRS:-15}"
SPARSE_MODE="${SPARSE_MODE:-false}"
WARMUP="${WARMUP:-5}"
RUNS="${RUNS:-10}"
CONDITION="${CONDITION:-default}"  # baseline / sparse / wide
STRATEGIES="${STRATEGIES:-preload multiset sequence-window spliterator-window sql-pivot}"
OUT_ROOT="${OUT_ROOT:-docs/benchmark/runs}"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
RUN_DIR="${OUT_ROOT}/${CONDITION}_${TIMESTAMP}"
SKIP_SEED="${SKIP_SEED:-auto}"
METRICS_WAIT="${METRICS_WAIT:-30}"  # Prometheusのscrapeを待つ秒数

echo "========================================="
echo "Benchmark Configuration"
echo "========================================="
echo "Condition: ${CONDITION}"
echo "Orders: ${ORDERS}, Attrs: ${ATTRS}, Sparse: ${SPARSE_MODE}"
echo "Strategies: ${STRATEGIES}"
echo "Warmup: ${WARMUP}, Measurement runs: ${RUNS}"
echo "Output: ${RUN_DIR}"
echo "========================================="

mkdir -p "${RUN_DIR}/compute" "${RUN_DIR}/file"

# Auto-detect if data already exists
if [[ "${SKIP_SEED}" == "auto" ]]; then
  echo "Checking existing data..."
  SUMMARY=$(curl -sS "${BASE_URL}/admin/summary" 2>/dev/null || echo '{}')
  CURRENT_ORDERS=$(echo "$SUMMARY" | jq -r '.orders // 0' 2>/dev/null || echo '0')
  CURRENT_ATTRS=$(echo "$SUMMARY" | jq -r '.attributeDefinitions // 0' 2>/dev/null || echo '0')

  if [[ "$CURRENT_ORDERS" -eq "$ORDERS" ]] && [[ "$CURRENT_ATTRS" -eq "$ATTRS" ]]; then
    echo "✓ Data already exists (${CURRENT_ORDERS} orders, ${CURRENT_ATTRS} attrs). Skipping seed."
    SKIP_SEED=1
  else
    echo "⚠ Data mismatch (current: ${CURRENT_ORDERS} orders, ${CURRENT_ATTRS} attrs; expected: ${ORDERS} orders, ${ATTRS} attrs)."
    echo "Please run seed manually:"
    if [[ "${SPARSE_MODE}" == "true" ]]; then
      echo "  curl -X DELETE \"${BASE_URL}/admin/truncate\""
      echo "  curl -X POST \"${BASE_URL}/admin/seed?customers=6000&orders=${ORDERS}&attrs=${ATTRS}&sparse=true\""
    else
      echo "  curl -X DELETE \"${BASE_URL}/admin/truncate\""
      echo "  curl -X POST \"${BASE_URL}/admin/seed?customers=6000&orders=${ORDERS}&attrs=${ATTRS}\""
    fi
    exit 1
  fi
fi

# Functions
call_benchmark_api() {
  local strategy="$1"
  local label="$2"  # warmup1 / run1 etc
  local outfile="${RUN_DIR}/compute/${strategy}-${label}.json"

  RESPONSE=$(curl -sS "${BASE_URL}/api/export/orders/attributes/benchmark?strategy=${strategy}")
  echo "$RESPONSE" | jq . > "${outfile}"

  local elapsed=$(echo "$RESPONSE" | jq -r '.elapsedMs')
  local md5=$(echo "$RESPONSE" | jq -r '.md5')

  echo "  [Compute] ${strategy} ${label}: ${elapsed}ms (MD5: ${md5:0:8}...)"
}

call_file_api() {
  local strategy="$1"
  local label="$2"
  local outfile="${RUN_DIR}/file/${strategy}-${label}.csv"
  local meta="${RUN_DIR}/file/${strategy}-${label}.json"

  START_TIME=$(date +%s%3N)
  curl -sS \
    -w '{"time_total":%{time_total},"size_download":%{size_download},"http_code":%{http_code}}\n' \
    -o "${outfile}" \
    "${BASE_URL}/api/export/orders/attributes?strategy=${strategy}" \
    > "${meta}"
  END_TIME=$(date +%s%3N)
  ELAPSED=$((END_TIME - START_TIME))

  MD5=$(md5sum "${outfile}" | awk '{print $1}')

  echo "  [File] ${strategy} ${label}: ${ELAPSED}ms (MD5: ${MD5:0:8}...)"
  echo "$MD5" > "${RUN_DIR}/file/${strategy}-${label}.md5"
}

# Convert strategies to array
STRATEGY_ARRAY=($STRATEGIES)

echo ""
echo "========================================="
echo "Phase 1: Warmup (${WARMUP} rounds)"
echo "========================================="

for round in $(seq 1 "${WARMUP}"); do
  echo "--- Warmup Round ${round}/${WARMUP} ---"

  # Round-robin: rotate strategies each round
  for idx in "${!STRATEGY_ARRAY[@]}"; do
    strategy_idx=$(( (idx + round - 1) % ${#STRATEGY_ARRAY[@]} ))
    strategy="${STRATEGY_ARRAY[$strategy_idx]}"

    # 系統1: Compute測定
    call_benchmark_api "${strategy}" "warmup${round}"

    # メトリクスのscrapeを待つ（warmup中は短めに）
    sleep 5
  done
done

echo ""
echo "Warmup completed. Waiting ${METRICS_WAIT}s for metrics stabilization..."
sleep "${METRICS_WAIT}"

echo ""
echo "========================================="
echo "Phase 2: Measurement (${RUNS} runs per strategy)"
echo "========================================="

for run in $(seq 1 "${RUNS}"); do
  echo "--- Measurement Run ${run}/${RUNS} ---"

  # Round-robin: rotate strategies each run
  for idx in "${!STRATEGY_ARRAY[@]}"; do
    strategy_idx=$(( (idx + run - 1) % ${#STRATEGY_ARRAY[@]} ))
    strategy="${STRATEGY_ARRAY[$strategy_idx]}"

    # 系統1: Compute測定（主軸）
    call_benchmark_api "${strategy}" "run${run}"

    # 系統2: 実ファイル出力（参考、5回に1回）
    if [[ $((run % 5)) -eq 1 ]]; then
      call_file_api "${strategy}" "run${run}"
    fi

    # メトリクスのscrapeを待つ
    if [[ "${run}" -lt "${RUNS}" ]] || [[ "${idx}" -lt $((${#STRATEGY_ARRAY[@]} - 1)) ]]; then
      echo "  Waiting ${METRICS_WAIT}s for Prometheus scrape..."
      sleep "${METRICS_WAIT}"
    fi
  done
done

echo ""
echo "========================================="
echo "Computing Summary Statistics"
echo "========================================="

# Compute summary for each strategy
SUMMARY_FILE="${RUN_DIR}/summary.txt"
echo "Benchmark Summary: ${CONDITION}" > "${SUMMARY_FILE}"
echo "Date: $(date)" >> "${SUMMARY_FILE}"
echo "Orders: ${ORDERS}, Attrs: ${ATTRS}, Sparse: ${SPARSE_MODE}" >> "${SUMMARY_FILE}"
echo "" >> "${SUMMARY_FILE}"

for strategy in ${STRATEGIES}; do
  echo "--- ${strategy} ---" >> "${SUMMARY_FILE}"

  # Extract elapsed times from compute measurements
  TIMES=()
  for run in $(seq 1 "${RUNS}"); do
    TIME=$(jq -r '.elapsedMs' "${RUN_DIR}/compute/${strategy}-run${run}.json" 2>/dev/null || echo "")
    if [[ -n "$TIME" ]]; then
      TIMES+=("$TIME")
    fi
  done

  if [[ ${#TIMES[@]} -gt 0 ]]; then
    # Sort times
    SORTED_TIMES=($(printf '%s\n' "${TIMES[@]}" | sort -n))

    # Calculate statistics
    COUNT=${#SORTED_TIMES[@]}
    MEDIAN_IDX=$((COUNT / 2))
    Q1_IDX=$((COUNT / 4))
    Q3_IDX=$((COUNT * 3 / 4))

    MEDIAN=${SORTED_TIMES[$MEDIAN_IDX]}
    Q1=${SORTED_TIMES[$Q1_IDX]}
    Q3=${SORTED_TIMES[$Q3_IDX]}
    MIN=${SORTED_TIMES[0]}
    MAX=${SORTED_TIMES[$((COUNT - 1))]}
    IQR=$((Q3 - Q1))

    echo "  Samples: ${COUNT}" >> "${SUMMARY_FILE}"
    echo "  Median: ${MEDIAN} ms" >> "${SUMMARY_FILE}"
    echo "  IQR: ${IQR} ms (Q1=${Q1}, Q3=${Q3})" >> "${SUMMARY_FILE}"
    echo "  Min: ${MIN} ms, Max: ${MAX} ms" >> "${SUMMARY_FILE}"

    # MD5 verification
    MD5=$(jq -r '.md5' "${RUN_DIR}/compute/${strategy}-run1.json" 2>/dev/null || echo "unknown")
    echo "  MD5: ${MD5}" >> "${SUMMARY_FILE}"
    echo "" >> "${SUMMARY_FILE}"

    echo "✓ ${strategy}: Median=${MEDIAN}ms, IQR=${IQR}ms, Max=${MAX}ms"
  else
    echo "⚠ ${strategy}: No data" >> "${SUMMARY_FILE}"
    echo "" >> "${SUMMARY_FILE}"
  fi
done

cat "${SUMMARY_FILE}"

echo ""
echo "========================================="
echo "Benchmark Complete!"
echo "========================================="
echo "Results: ${RUN_DIR}"
echo ""
echo "⚠️ IMPORTANT: Wait 30-60 seconds, then record metrics from Prometheus/Grafana:"
echo ""
echo "  1. JVM Heap Peak:"
echo "     max_over_time(sum(jvm_memory_used_bytes{area=\"heap\"})[30m])"
echo ""
echo "  2. GC Pause Total:"
echo "     increase(jvm_gc_pause_seconds_sum[30m])"
echo ""
echo "See docs/benchmark/measurement-guide.md for details."
echo ""
