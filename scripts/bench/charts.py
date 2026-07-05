#!/usr/bin/env python3
"""Generate benchmark charts from a persisted run artifact directory."""

from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
from typing import Any

import matplotlib

matplotlib.use("Agg")

import matplotlib.pyplot as plt
import pandas as pd
import seaborn as sns


BYTES_PER_MIB = 1024 * 1024
DEFAULT_FORMATS = ("svg", "png")
STRATEGY_LABELS = {
    "preload": "PRELOAD",
    "multiset": "MULTISET",
    "sequence-window": "SEQUENCE_WINDOW",
    "spliterator-window": "SPLITERATOR_WINDOW",
    "sql-pivot": "SQL_PIVOT",
    "imperative-result-set": "RESULT_SET",
    "json-aggregation": "JSON_AGG",
}


def main() -> int:
    args = parse_args()
    run_dir = Path(args.run_dir)
    if not run_dir.exists():
        raise SystemExit(f"Run directory does not exist: {run_dir}")

    charts_dir = run_dir / "charts"
    charts_dir.mkdir(parents=True, exist_ok=True)

    configure_style()
    summary = read_json(run_dir / "summary.json")
    samples = pd.read_csv(run_dir / "samples.csv")
    formats = parse_formats(args.formats)

    generated: list[Path] = []
    generated += plot_elapsed_median_iqr(summary, charts_dir, formats, args.dpi)
    generated += plot_elapsed_samples(samples, charts_dir, formats, args.dpi)
    generated += plot_prometheus_series(
        run_dir,
        metric_name="heap-used-bytes",
        chart_name="heap-used-over-time",
        title="Heap Used Over Time",
        y_label="Heap used (MiB)",
        transform=lambda value: value / BYTES_PER_MIB,
        charts_dir=charts_dir,
        formats=formats,
        dpi=args.dpi,
    )
    generated += plot_prometheus_counter_rate(
        run_dir,
        metric_name="gc-pause-seconds-sum",
        chart_name="gc-pause-over-time",
        title="GC Pause Increase Over Time",
        y_label="GC pause increase (ms)",
        multiplier=1000.0,
        charts_dir=charts_dir,
        formats=formats,
        dpi=args.dpi,
    )
    generated += plot_prometheus_counter_rate(
        run_dir,
        metric_name="allocation-bytes-total",
        chart_name="allocation-rate-over-time",
        title="Allocation Rate Over Time",
        y_label="Allocation rate (MiB/s)",
        multiplier=1.0 / BYTES_PER_MIB,
        divide_by_elapsed=True,
        charts_dir=charts_dir,
        formats=formats,
        dpi=args.dpi,
    )
    generated += plot_prometheus_series(
        run_dir,
        metric_name="process-cpu-usage",
        chart_name="process-cpu-over-time",
        title="Process CPU Over Time",
        y_label="Process CPU (%)",
        transform=lambda value: value * 100.0,
        charts_dir=charts_dir,
        formats=formats,
        dpi=args.dpi,
    )

    write_index(run_dir, generated)

    print(f"Generated {len(generated)} chart files in {charts_dir}")
    for path in generated:
        print(path)
    return 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate charts from benchmark run artifacts.")
    parser.add_argument("--run-dir", required=True, help="Path to docs/benchmark/runs/<run-id>")
    parser.add_argument("--formats", default="svg,png", help="Comma-separated output formats")
    parser.add_argument("--dpi", type=int, default=180, help="DPI for raster outputs")
    return parser.parse_args()


def parse_formats(raw: str) -> tuple[str, ...]:
    formats = tuple(item.strip().lower() for item in raw.split(",") if item.strip())
    return formats or DEFAULT_FORMATS


def configure_style() -> None:
    sns.set_theme(
        context="notebook",
        style="whitegrid",
        palette="colorblind",
        rc={
            "figure.facecolor": "white",
            "axes.facecolor": "white",
            "axes.edgecolor": "#263238",
            "axes.labelcolor": "#263238",
            "text.color": "#263238",
            "xtick.color": "#263238",
            "ytick.color": "#263238",
            "font.family": "DejaVu Sans",
            "axes.titlesize": 16,
            "axes.labelsize": 13,
            "xtick.labelsize": 11,
            "ytick.labelsize": 12,
        },
    )


def read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def strategy_label(strategy: str) -> str:
    return STRATEGY_LABELS.get(strategy, strategy)


def strategy_order_from_summary(summary: dict[str, Any]) -> list[str]:
    strategies = summary.get("strategies", {})
    return sorted(
        strategies.keys(),
        key=lambda strategy: strategies[strategy].get("elapsedMs", {}).get("median") or math.inf,
    )


def plot_elapsed_median_iqr(
    summary: dict[str, Any],
    charts_dir: Path,
    formats: tuple[str, ...],
    dpi: int,
) -> list[Path]:
    order = strategy_order_from_summary(summary)
    rows = []
    for strategy in order:
        elapsed = summary["strategies"][strategy]["elapsedMs"]
        median = elapsed.get("median")
        if median is None:
            continue
        rows.append(
            {
                "strategy": strategy,
                "label": strategy_label(strategy),
                "median": median,
                "q1": elapsed.get("q1") or median,
                "q3": elapsed.get("q3") or median,
            }
        )
    if not rows:
        return []

    data = pd.DataFrame(rows)
    fig, ax = plt.subplots(figsize=(10, max(4.8, len(data) * 0.62)))
    positions = range(len(data))
    lower_error = data["median"] - data["q1"]
    upper_error = data["q3"] - data["median"]

    ax.barh(positions, data["median"], color=sns.color_palette("colorblind", len(data)))
    ax.errorbar(
        data["median"],
        positions,
        xerr=[lower_error, upper_error],
        fmt="none",
        ecolor="#263238",
        capsize=4,
        linewidth=1.4,
    )
    ax.set_yticks(list(positions), data["label"])
    ax.invert_yaxis()
    ax.set_xlabel("Elapsed time median (ms), error bar = IQR")
    ax.set_ylabel("")
    ax.set_title(f"Elapsed Time by Strategy: {summary['condition']}")
    annotate_bars(ax, data["median"], positions)
    sns.despine(left=True, bottom=False)
    fig.tight_layout()
    return save_figure(fig, charts_dir / "elapsed-median-iqr", formats, dpi)


def annotate_bars(ax: plt.Axes, values: pd.Series, positions: range) -> None:
    max_value = max(values)
    offset = max_value * 0.015
    for position, value in zip(positions, values):
        ax.text(value + offset, position, f"{int(value)} ms", va="center", fontsize=10)


def plot_elapsed_samples(
    samples: pd.DataFrame,
    charts_dir: Path,
    formats: tuple[str, ...],
    dpi: int,
) -> list[Path]:
    measurement = samples[(samples["phase"] == "measurement") & (samples["status"] == "ok")].copy()
    measurement = measurement.dropna(subset=["elapsedMs"])
    if measurement.empty:
        return []

    order = (
        measurement.groupby("strategy")["elapsedMs"]
        .median()
        .sort_values()
        .index
        .tolist()
    )
    measurement["label"] = measurement["strategy"].map(strategy_label)
    label_order = [strategy_label(strategy) for strategy in order]

    fig, ax = plt.subplots(figsize=(10, max(4.8, len(order) * 0.62)))
    sns.boxplot(
        data=measurement,
        x="elapsedMs",
        y="label",
        order=label_order,
        color="#dfe7ef",
        fliersize=0,
        ax=ax,
    )
    sns.stripplot(
        data=measurement,
        x="elapsedMs",
        y="label",
        order=label_order,
        color="#0b5d7e",
        size=6,
        alpha=0.8,
        ax=ax,
    )
    ax.set_xlabel("Elapsed time per measurement sample (ms)")
    ax.set_ylabel("")
    ax.set_title("Elapsed Time Samples")
    sns.despine(left=True, bottom=False)
    fig.tight_layout()
    return save_figure(fig, charts_dir / "elapsed-samples", formats, dpi)


def plot_prometheus_series(
    run_dir: Path,
    metric_name: str,
    chart_name: str,
    title: str,
    y_label: str,
    transform,
    charts_dir: Path,
    formats: tuple[str, ...],
    dpi: int,
) -> list[Path]:
    data = load_prometheus_metric(run_dir, metric_name)
    if data.empty:
        return []

    data["value"] = data["value"].map(transform)
    fig, ax = plt.subplots(figsize=(10, 4.8))
    sns.lineplot(data=data, x="elapsedSec", y="value", ax=ax, linewidth=2.0)
    ax.set_xlabel("Seconds from first sample")
    ax.set_ylabel(y_label)
    ax.set_title(title)
    sns.despine(left=False, bottom=False)
    fig.tight_layout()
    return save_figure(fig, charts_dir / chart_name, formats, dpi)


def plot_prometheus_counter_rate(
    run_dir: Path,
    metric_name: str,
    chart_name: str,
    title: str,
    y_label: str,
    multiplier: float,
    charts_dir: Path,
    formats: tuple[str, ...],
    dpi: int,
    divide_by_elapsed: bool = False,
) -> list[Path]:
    data = load_prometheus_metric(run_dir, metric_name)
    if len(data) < 2:
        return []

    data = data.sort_values("timestamp").copy()
    data["deltaValue"] = data["value"].diff()
    data["deltaSec"] = data["timestamp"].diff()
    if divide_by_elapsed:
        data["plotValue"] = (data["deltaValue"] / data["deltaSec"]) * multiplier
    else:
        data["plotValue"] = data["deltaValue"] * multiplier
    data = data.replace([math.inf, -math.inf], pd.NA).dropna(subset=["plotValue"])
    data = data[data["plotValue"] >= 0]
    if data.empty:
        return []

    fig, ax = plt.subplots(figsize=(10, 4.8))
    sns.lineplot(data=data, x="elapsedSec", y="plotValue", ax=ax, linewidth=2.0)
    ax.set_xlabel("Seconds from first sample")
    ax.set_ylabel(y_label)
    ax.set_title(title)
    sns.despine(left=False, bottom=False)
    fig.tight_layout()
    return save_figure(fig, charts_dir / chart_name, formats, dpi)


def load_prometheus_metric(run_dir: Path, metric_name: str) -> pd.DataFrame:
    path = run_dir / "prometheus" / "range" / f"{metric_name}.json"
    if not path.exists():
        return pd.DataFrame(columns=["timestamp", "elapsedSec", "value"])

    payload = read_json(path)
    rows = []
    for series in payload.get("data", {}).get("result", []):
        for timestamp, raw_value in series.get("values", []):
            try:
                value = float(raw_value)
                if math.isnan(value):
                    continue
                rows.append({"timestamp": float(timestamp), "value": value})
            except (TypeError, ValueError):
                continue
    if not rows:
        return pd.DataFrame(columns=["timestamp", "elapsedSec", "value"])

    data = pd.DataFrame(rows).sort_values("timestamp")
    data["elapsedSec"] = data["timestamp"] - data["timestamp"].min()
    return data


def save_figure(
    fig: plt.Figure,
    base_path: Path,
    formats: tuple[str, ...],
    dpi: int,
) -> list[Path]:
    paths = []
    for output_format in formats:
        path = base_path.with_suffix(f".{output_format}")
        fig.savefig(path, dpi=dpi, bbox_inches="tight")
        paths.append(path)
    plt.close(fig)
    return paths


def write_index(run_dir: Path, generated: list[Path]) -> None:
    charts_dir = run_dir / "charts"
    lines = [
        f"# Charts: {run_dir.name}",
        "",
        "Generated files:",
        "",
    ]
    for path in generated:
        lines.append(f"- `{path.name}`")
    (charts_dir / "README.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    raise SystemExit(main())
