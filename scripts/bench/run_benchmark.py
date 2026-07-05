#!/usr/bin/env python3
"""Run attribute export benchmarks and persist reproducible run artifacts.

This runner intentionally keeps dependencies to the Python standard library.
It treats benchmark API responses as the source of truth for elapsed time, and
Prometheus as supplemental time-series evidence for heap, GC, allocation, and
process metrics.
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import platform
import statistics
import subprocess
import sys
import time
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Optional
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


DEFAULT_STRATEGIES = "preload,multiset,sequence-window,spliterator-window"
FIXED_COLUMN_COUNT = 5
PROMETHEUS_JOB = "simple-ec-backend"


@dataclass
class Sample:
    sampleId: str
    phase: str
    round: int
    strategy: str
    startEpochSec: float
    endEpochSec: float
    elapsedMs: Optional[int]
    md5: Optional[str]
    rows: int
    attrs: int
    fixedColumns: int
    cells: int
    bytes: Optional[int]
    status: str
    error: Optional[str] = None


@dataclass
class PrometheusQuery:
    name: str
    expr: str
    kind: str
    required: bool = False


PROMETHEUS_RANGE_QUERIES = [
    PrometheusQuery(
        name="heap-used-bytes",
        expr=f'sum(jvm_memory_used_bytes{{area="heap",job="{PROMETHEUS_JOB}"}})',
        kind="gauge",
        required=True,
    ),
    PrometheusQuery(
        name="heap-committed-bytes",
        expr=f'sum(jvm_memory_committed_bytes{{area="heap",job="{PROMETHEUS_JOB}"}})',
        kind="gauge",
    ),
    PrometheusQuery(
        name="heap-max-bytes",
        expr=f'sum(jvm_memory_max_bytes{{area="heap",job="{PROMETHEUS_JOB}"}})',
        kind="gauge",
    ),
    PrometheusQuery(
        name="gc-pause-seconds-sum",
        expr=f'sum(jvm_gc_pause_seconds_sum{{job="{PROMETHEUS_JOB}"}})',
        kind="counter",
        required=True,
    ),
    PrometheusQuery(
        name="gc-pause-count",
        expr=f'sum(jvm_gc_pause_seconds_count{{job="{PROMETHEUS_JOB}"}})',
        kind="counter",
    ),
    PrometheusQuery(
        name="allocation-bytes-total",
        expr=f'sum(jvm_gc_memory_allocated_bytes_total{{job="{PROMETHEUS_JOB}"}})',
        kind="counter",
    ),
    PrometheusQuery(
        name="process-cpu-usage",
        expr=f'process_cpu_usage{{job="{PROMETHEUS_JOB}"}}',
        kind="gauge",
    ),
    PrometheusQuery(
        name="system-cpu-usage",
        expr=f'system_cpu_usage{{job="{PROMETHEUS_JOB}"}}',
        kind="gauge",
    ),
    PrometheusQuery(
        name="hikaricp-connections-active",
        expr=f'hikaricp_connections_active{{job="{PROMETHEUS_JOB}"}}',
        kind="gauge",
    ),
    PrometheusQuery(
        name="http-latency-p50",
        expr=(
            "histogram_quantile(0.50, "
            f'sum by (le) (rate(http_server_requests_seconds_bucket{{job="{PROMETHEUS_JOB}",'
            'uri="/api/export/orders/attributes/benchmark"}[1m])))'
        ),
        kind="gauge",
    ),
    PrometheusQuery(
        name="http-latency-p95",
        expr=(
            "histogram_quantile(0.95, "
            f'sum by (le) (rate(http_server_requests_seconds_bucket{{job="{PROMETHEUS_JOB}",'
            'uri="/api/export/orders/attributes/benchmark"}[1m])))'
        ),
        kind="gauge",
    ),
]


def main() -> int:
    args = parse_args()
    if args.mode != "round-robin":
        raise SystemExit("Only --mode round-robin is implemented in this P0 runner.")

    strategies = parse_strategies(args.strategies)
    run_id = args.run_id or build_run_id(args.condition, args.mode)
    run_dir = Path(args.out_root) / run_id
    create_run_directories(run_dir)

    print("=========================================")
    print("Benchmark Artifact Runner")
    print("=========================================")
    print(f"Run ID: {run_id}")
    print(f"Condition: {args.condition}")
    print(f"Mode: {args.mode}")
    print(f"Orders: {args.orders}, Attrs: {args.attrs}, Sparse: {args.sparse}")
    print(f"Strategies: {', '.join(strategies)}")
    print(f"Output: {run_dir}")
    print("=========================================")

    if not args.skip_data_check:
        validate_dataset(args.base_url, args.orders, args.attrs)

    start_epoch = time.time()
    write_json(run_dir / "environment.json", collect_environment(args))

    samples: list[Sample] = []
    try:
        samples.extend(run_phase(args, run_dir, strategies, phase="warmup", rounds=args.warmup))

        if args.warmup and args.metrics_wait > 0:
            print(f"Warmup completed. Waiting {args.metrics_wait}s before measurement...")
            time.sleep(args.metrics_wait)

        samples.extend(run_phase(args, run_dir, strategies, phase="measurement", rounds=args.runs))
    finally:
        end_epoch = time.time()

    run_metadata = build_run_metadata(args, run_id, strategies, start_epoch, end_epoch)
    write_json(run_dir / "run.json", run_metadata)
    write_samples(run_dir, samples)

    if args.prometheus_url:
        collect_prometheus_range(args, run_dir, start_epoch, end_epoch)

    summary = build_summary(args, run_id, samples, run_dir)
    write_json(run_dir / "summary.json", summary)
    write_summary_markdown(run_dir / "summary.md", summary)

    print("")
    print("Benchmark complete.")
    print(f"Artifacts: {run_dir}")
    print(f"Summary: {run_dir / 'summary.md'}")
    return 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run reproducible attribute export benchmarks.")
    parser.add_argument("--base-url", default=os.environ.get("BASE_URL", "http://localhost:8080"))
    parser.add_argument("--prometheus-url", default=os.environ.get("PROMETHEUS_URL", "http://localhost:9090"))
    parser.add_argument("--condition", default=os.environ.get("CONDITION", "default"))
    parser.add_argument("--mode", default=os.environ.get("MODE", "round-robin"))
    parser.add_argument("--orders", type=int, default=int(os.environ.get("ORDERS", "5000")))
    parser.add_argument("--attrs", type=int, default=int(os.environ.get("ATTRS", "15")))
    parser.add_argument("--sparse", action="store_true", default=os.environ.get("SPARSE_MODE", "false") == "true")
    parser.add_argument("--warmup", type=int, default=int(os.environ.get("WARMUP", "5")))
    parser.add_argument("--runs", type=int, default=int(os.environ.get("RUNS", "10")))
    parser.add_argument("--strategies", default=os.environ.get("STRATEGIES", DEFAULT_STRATEGIES))
    parser.add_argument("--out-root", default=os.environ.get("OUT_ROOT", "docs/benchmark/runs"))
    parser.add_argument("--run-id", default=os.environ.get("RUN_ID"))
    parser.add_argument("--metrics-wait", type=int, default=int(os.environ.get("METRICS_WAIT", "30")))
    parser.add_argument("--sample-wait", type=int, default=int(os.environ.get("SAMPLE_WAIT", "0")))
    parser.add_argument("--prometheus-step", default=os.environ.get("PROMETHEUS_STEP", "5s"))
    parser.add_argument("--prometheus-padding", type=int, default=int(os.environ.get("PROMETHEUS_PADDING", "30")))
    parser.add_argument("--skip-data-check", action="store_true")
    return parser.parse_args()


def parse_strategies(raw: str) -> list[str]:
    strategies = [item.strip() for item in raw.replace(" ", ",").split(",") if item.strip()]
    if not strategies:
        raise SystemExit("At least one strategy is required.")
    return strategies


def build_run_id(condition: str, mode: str) -> str:
    timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    return f"{timestamp}_{condition}_{mode}"


def create_run_directories(run_dir: Path) -> None:
    for child in [
        run_dir,
        run_dir / "prometheus",
        run_dir / "prometheus" / "range",
        run_dir / "artifacts",
    ]:
        child.mkdir(parents=True, exist_ok=True)


def validate_dataset(base_url: str, orders: int, attrs: int) -> None:
    summary = http_json(f"{base_url}/admin/summary")
    current_orders = int(summary.get("orders", 0))
    current_attrs = int(summary.get("attributeDefinitions", 0))
    if current_orders != orders or current_attrs != attrs:
        raise SystemExit(
            "Dataset mismatch. "
            f"current orders={current_orders}, attrs={current_attrs}; "
            f"expected orders={orders}, attrs={attrs}. "
            "Prepare data first or pass --skip-data-check."
        )
    print(f"Dataset OK: orders={current_orders}, attrs={current_attrs}")


def run_phase(
    args: argparse.Namespace,
    run_dir: Path,
    strategies: list[str],
    phase: str,
    rounds: int,
) -> list[Sample]:
    samples: list[Sample] = []
    if rounds <= 0:
        return samples

    print("")
    print(f"Phase: {phase} ({rounds} rounds)")
    for round_number in range(1, rounds + 1):
        print(f"--- {phase} round {round_number}/{rounds} ---")
        for idx in range(len(strategies)):
            strategy_idx = (idx + round_number - 1) % len(strategies)
            strategy = strategies[strategy_idx]
            sample = call_benchmark_api(args, strategy, phase, round_number)
            samples.append(sample)
            append_jsonl(run_dir / "samples.jsonl", asdict(sample))
            print_sample(sample)
            if args.sample_wait > 0:
                time.sleep(args.sample_wait)
    return samples


def call_benchmark_api(args: argparse.Namespace, strategy: str, phase: str, round_number: int) -> Sample:
    sample_id = f"{phase}-{round_number:02d}-{strategy}"
    url = f"{args.base_url}/api/export/orders/attributes/benchmark?{urlencode({'strategy': strategy})}"
    start = time.time()
    try:
        response = http_json(url)
        end = time.time()
        elapsed_ms = int(response["elapsedMs"])
        md5 = str(response["md5"])
        status = "ok"
        error = None
    except (HTTPError, URLError, TimeoutError, KeyError, ValueError) as exc:
        end = time.time()
        elapsed_ms = None
        md5 = None
        status = "error"
        error = str(exc)

    return Sample(
        sampleId=sample_id,
        phase=phase,
        round=round_number,
        strategy=strategy,
        startEpochSec=start,
        endEpochSec=end,
        elapsedMs=elapsed_ms,
        md5=md5,
        rows=args.orders,
        attrs=args.attrs,
        fixedColumns=FIXED_COLUMN_COUNT,
        cells=args.orders * (FIXED_COLUMN_COUNT + args.attrs),
        bytes=None,
        status=status,
        error=error,
    )


def print_sample(sample: Sample) -> None:
    if sample.status == "ok":
        duration = f"{sample.elapsedMs}ms"
        md5 = (sample.md5 or "")[:8]
        print(f"  {sample.strategy} {sample.phase}{sample.round}: {duration} (MD5: {md5}...)")
    else:
        print(f"  {sample.strategy} {sample.phase}{sample.round}: ERROR {sample.error}")


def collect_prometheus_range(
    args: argparse.Namespace,
    run_dir: Path,
    start_epoch: float,
    end_epoch: float,
) -> None:
    start = max(0, start_epoch - args.prometheus_padding)
    end = end_epoch + args.prometheus_padding
    queries = [asdict(query) for query in PROMETHEUS_RANGE_QUERIES]
    write_json(run_dir / "prometheus" / "queries.json", queries)

    print("")
    print("Collecting Prometheus range query artifacts...")
    for query in PROMETHEUS_RANGE_QUERIES:
        payload = prometheus_query_range(
            prometheus_url=args.prometheus_url,
            query=query.expr,
            start=start,
            end=end,
            step=args.prometheus_step,
        )
        payload["_benchmarkQuery"] = asdict(query)
        write_json(run_dir / "prometheus" / "range" / f"{query.name}.json", payload)
        status = payload.get("status", "unknown")
        series = len(payload.get("data", {}).get("result", []))
        print(f"  {query.name}: {status}, series={series}")


def prometheus_query_range(
    prometheus_url: str,
    query: str,
    start: float,
    end: float,
    step: str,
) -> dict[str, Any]:
    params = urlencode({"query": query, "start": start, "end": end, "step": step})
    url = f"{prometheus_url.rstrip('/')}/api/v1/query_range?{params}"
    try:
        return http_json(url)
    except (HTTPError, URLError, TimeoutError, ValueError) as exc:
        return {"status": "error", "error": str(exc), "data": {"result": []}}


def build_summary(
    args: argparse.Namespace,
    run_id: str,
    samples: list[Sample],
    run_dir: Path,
) -> dict[str, Any]:
    measurement_samples = [sample for sample in samples if sample.phase == "measurement"]
    summary: dict[str, Any] = {
        "runId": run_id,
        "condition": args.condition,
        "mode": args.mode,
        "dataset": dataset_metadata(args),
        "strategies": {},
        "prometheus": summarize_prometheus(run_dir / "prometheus" / "range"),
    }

    for strategy in sorted({sample.strategy for sample in measurement_samples}):
        strategy_samples = [
            sample for sample in measurement_samples if sample.strategy == strategy and sample.status == "ok"
        ]
        elapsed = [sample.elapsedMs for sample in strategy_samples if sample.elapsedMs is not None]
        md5_values = sorted({sample.md5 for sample in strategy_samples if sample.md5})
        summary["strategies"][strategy] = {
            "samples": len(strategy_samples),
            "elapsedMs": summarize_numbers(elapsed),
            "md5": {
                "value": md5_values[0] if len(md5_values) == 1 else None,
                "allMatched": len(md5_values) == 1,
                "values": md5_values,
            },
            "rows": args.orders,
            "attrs": args.attrs,
            "cells": args.orders * (FIXED_COLUMN_COUNT + args.attrs),
        }

    return summary


def summarize_numbers(values: list[int]) -> dict[str, Optional[int]]:
    if not values:
        return {"median": None, "q1": None, "q3": None, "iqr": None, "min": None, "max": None}

    sorted_values = sorted(values)
    median = int(statistics.median(sorted_values))
    q1 = sorted_values[len(sorted_values) // 4]
    q3 = sorted_values[(len(sorted_values) * 3) // 4]
    return {
        "median": median,
        "q1": q1,
        "q3": q3,
        "iqr": q3 - q1,
        "min": sorted_values[0],
        "max": sorted_values[-1],
    }


def summarize_prometheus(range_dir: Path) -> dict[str, Any]:
    if not range_dir.exists():
        return {}

    result: dict[str, Any] = {}
    for path in sorted(range_dir.glob("*.json")):
        payload = read_json(path)
        query = payload.get("_benchmarkQuery", {})
        values = extract_prometheus_values(payload)
        if not values:
            result[path.stem] = {
                "kind": query.get("kind"),
                "series": 0,
                "available": False,
            }
            continue

        numeric_values = [value for _, value in values]
        item: dict[str, Any] = {
            "kind": query.get("kind"),
            "series": len(payload.get("data", {}).get("result", [])),
            "available": True,
            "min": min(numeric_values),
            "max": max(numeric_values),
            "first": numeric_values[0],
            "last": numeric_values[-1],
        }
        if query.get("kind") == "counter":
            item["increase"] = numeric_values[-1] - numeric_values[0]
        result[path.stem] = item
    return result


def extract_prometheus_values(payload: dict[str, Any]) -> list[tuple[float, float]]:
    values: list[tuple[float, float]] = []
    for series in payload.get("data", {}).get("result", []):
        for timestamp, raw_value in series.get("values", []):
            try:
                values.append((float(timestamp), float(raw_value)))
            except (TypeError, ValueError):
                continue
    values.sort(key=lambda item: item[0])
    return values


def write_summary_markdown(path: Path, summary: dict[str, Any]) -> None:
    lines = [
        f"# Benchmark Summary: {summary['runId']}",
        "",
        f"- Condition: `{summary['condition']}`",
        f"- Mode: `{summary['mode']}`",
        f"- Orders: `{summary['dataset']['orders']}`",
        f"- Attrs: `{summary['dataset']['attrs']}`",
        f"- Cells per sample: `{summary['dataset']['cells']}`",
        "",
        "## Elapsed Time",
        "",
        "| Strategy | Samples | Median ms | IQR ms | Min ms | Max ms | MD5 matched |",
        "| --- | ---: | ---: | ---: | ---: | ---: | --- |",
    ]
    for strategy, values in sorted(summary["strategies"].items()):
        elapsed = values["elapsedMs"]
        md5 = values["md5"]
        lines.append(
            "| {strategy} | {samples} | {median} | {iqr} | {min_} | {max_} | {matched} |".format(
                strategy=strategy,
                samples=values["samples"],
                median=value_or_dash(elapsed["median"]),
                iqr=value_or_dash(elapsed["iqr"]),
                min_=value_or_dash(elapsed["min"]),
                max_=value_or_dash(elapsed["max"]),
                matched="yes" if md5["allMatched"] else "no",
            )
        )

    lines.extend(["", "## Prometheus Summary", ""])
    if summary.get("prometheus"):
        lines.extend(
            [
                "| Metric | Available | Max | Increase |",
                "| --- | --- | ---: | ---: |",
            ]
        )
        for name, values in sorted(summary["prometheus"].items()):
            lines.append(
                "| {name} | {available} | {max_} | {increase} |".format(
                    name=name,
                    available="yes" if values.get("available") else "no",
                    max_=format_float(values.get("max")),
                    increase=format_float(values.get("increase")),
                )
            )
    else:
        lines.append("Prometheus artifacts were not collected.")

    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def value_or_dash(value: Any) -> Any:
    return "-" if value is None else value


def format_float(value: Any) -> str:
    if value is None:
        return "-"
    if isinstance(value, (int, float)):
        return f"{value:.6g}"
    return str(value)


def build_run_metadata(
    args: argparse.Namespace,
    run_id: str,
    strategies: list[str],
    start_epoch: float,
    end_epoch: float,
) -> dict[str, Any]:
    return {
        "runId": run_id,
        "createdAt": iso_now(),
        "mode": args.mode,
        "condition": args.condition,
        "dataset": dataset_metadata(args),
        "strategies": strategies,
        "warmup": args.warmup,
        "measurementRuns": args.runs,
        "baseUrl": args.base_url,
        "prometheusUrl": args.prometheus_url,
        "time": {
            "startEpochSec": start_epoch,
            "endEpochSec": end_epoch,
            "durationSec": end_epoch - start_epoch,
        },
        "git": git_metadata(),
    }


def dataset_metadata(args: argparse.Namespace) -> dict[str, Any]:
    return {
        "orders": args.orders,
        "attrs": args.attrs,
        "sparse": args.sparse,
        "fixedColumns": FIXED_COLUMN_COUNT,
        "cells": args.orders * (FIXED_COLUMN_COUNT + args.attrs),
    }


def collect_environment(args: argparse.Namespace) -> dict[str, Any]:
    return {
        "createdAt": iso_now(),
        "platform": {
            "system": platform.system(),
            "release": platform.release(),
            "machine": platform.machine(),
            "processor": platform.processor(),
            "python": platform.python_version(),
            "cpuCount": os.cpu_count(),
        },
        "java": command_output(["java", "-version"]),
        "docker": {
            "composeFile": "compose.yaml",
        },
        "jvmArgs": os.environ.get("JAVA_TOOL_OPTIONS"),
        "prometheus": {
            "url": args.prometheus_url,
            "step": args.prometheus_step,
            "paddingSec": args.prometheus_padding,
        },
        "git": git_metadata(),
    }


def git_metadata() -> dict[str, Optional[str]]:
    return {
        "branch": command_output(["git", "branch", "--show-current"]),
        "commit": command_output(["git", "rev-parse", "HEAD"]),
        "dirty": command_output(["git", "status", "--short"]),
    }


def command_output(command: list[str]) -> Optional[str]:
    try:
        completed = subprocess.run(command, check=False, capture_output=True, text=True)
    except FileNotFoundError:
        return None
    output = "\n".join(part for part in [completed.stdout.strip(), completed.stderr.strip()] if part)
    return output or None


def iso_now() -> str:
    return datetime.now(timezone.utc).astimezone().isoformat()


def http_json(url: str) -> dict[str, Any]:
    request = Request(url, headers={"Accept": "application/json"})
    with urlopen(request, timeout=30) as response:
        body = response.read().decode("utf-8")
    return json.loads(body)


def write_samples(run_dir: Path, samples: list[Sample]) -> None:
    rows = [asdict(sample) for sample in samples]
    write_json(run_dir / "samples.json", rows)
    write_jsonl(run_dir / "samples.jsonl", rows)
    write_csv(run_dir / "samples.csv", rows)


def write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def append_jsonl(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as file:
        file.write(json.dumps(payload, ensure_ascii=False, sort_keys=True) + "\n")


def write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as file:
        for row in rows:
            file.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")


def write_csv(path: Path, rows: list[dict[str, Any]]) -> None:
    if not rows:
        path.write_text("", encoding="utf-8")
        return

    with path.open("w", encoding="utf-8", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)


if __name__ == "__main__":
    sys.exit(main())
