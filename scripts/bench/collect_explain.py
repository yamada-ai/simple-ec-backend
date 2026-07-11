#!/usr/bin/env python3
"""Collect PostgreSQL EXPLAIN artifacts for benchmark query shapes."""

from __future__ import annotations

import argparse
import csv
import json
import re
import subprocess
import sys
from pathlib import Path
from typing import Any, Optional


DEFAULT_STRATEGIES = "vertical-join,sql-pivot,json-aggregation,preload-base,preload-attributes"
BASE_FIELDS = [
    "o.id AS order_id",
    "o.customer_id",
    "c.name AS customer_name",
    "c.email AS customer_email",
    "o.order_date",
]
BASE_FROM = 'FROM "order" o JOIN customer c ON c.id = o.customer_id'
BASE_GROUP_BY = "o.id, o.customer_id, c.name, c.email, o.order_date"


def main() -> int:
    args = parse_args()
    explain_dir = Path(args.run_dir) / "explain"
    explain_dir.mkdir(parents=True, exist_ok=True)
    artifact_suffix = build_artifact_suffix(args)

    definition_ids = fetch_definition_ids(args)
    strategies = parse_strategies(args.strategies)

    print("=========================================")
    print("EXPLAIN Artifact Collector")
    print("=========================================")
    print(f"Run dir: {args.run_dir}")
    print(f"Strategies: {', '.join(strategies)}")
    print(f"Attribute definitions: {len(definition_ids)}")
    print(f"Runs: {args.runs}")
    if args.pg_setting:
        print(f"Session settings: {', '.join(args.pg_setting)}")
    print("=========================================")

    measurements: list[dict[str, Any]] = []
    for strategy in strategies:
        sql = build_sql(strategy, definition_ids)
        if sql is None:
            print(f"Skipping unknown strategy: {strategy}")
            continue

        for run_number in range(1, args.runs + 1):
            run_suffix = artifact_suffix
            if args.runs > 1:
                run_suffix = f"{artifact_suffix}.run{run_number:02d}"

            sql_path = explain_dir / f"{strategy}{run_suffix}.sql"
            json_path = explain_dir / f"{strategy}{run_suffix}.explain.json"
            text_path = explain_dir / f"{strategy}{run_suffix}.explain.txt"
            sql_path.write_text(session_prelude(args) + sql + "\n", encoding="utf-8")

            print(f"Collecting {strategy} run {run_number}/{args.runs}...")
            json_explain = run_psql(args, explain(sql, "JSON"))
            json_path.write_text(json_explain, encoding="utf-8")
            text_explain = run_psql(args, explain(sql, "TEXT"))
            text_path.write_text(text_explain, encoding="utf-8")
            measurements.append(
                summarize_explain_text(
                    strategy=strategy,
                    run_number=run_number,
                    setting_label=artifact_suffix.lstrip("."),
                    path=text_path,
                    text=text_explain,
                )
            )

    if measurements:
        write_measurements(explain_dir, artifact_suffix, measurements)

    print("EXPLAIN artifacts complete.")
    print(f"Artifacts: {explain_dir}")
    return 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Collect EXPLAIN ANALYZE artifacts.")
    parser.add_argument("--run-dir", required=True)
    parser.add_argument("--strategies", default=DEFAULT_STRATEGIES)
    parser.add_argument("--db-service", default="postgres")
    parser.add_argument("--db-user", default="postgres")
    parser.add_argument("--db-name", default="simple_ec")
    parser.add_argument("--timeout", type=int, default=600)
    parser.add_argument("--runs", type=int, default=1)
    parser.add_argument(
        "--pg-setting",
        action="append",
        default=[],
        help="PostgreSQL session setting for this run, e.g. --pg-setting jit=off",
    )
    parser.add_argument(
        "--label",
        default="",
        help="Artifact filename suffix. Defaults to a suffix derived from --pg-setting.",
    )
    return parser.parse_args()


def parse_strategies(raw: str) -> list[str]:
    return [item.strip() for item in raw.replace(" ", ",").split(",") if item.strip()]


def build_artifact_suffix(args: argparse.Namespace) -> str:
    if args.label:
        return f".{sanitize_filename(args.label)}"
    if args.pg_setting:
        return "." + sanitize_filename("-".join(args.pg_setting))
    return ""


def fetch_definition_ids(args: argparse.Namespace) -> list[int]:
    output = run_psql(
        args,
        "SELECT id FROM order_attribute_definition ORDER BY id",
        tuples_only=True,
    )
    return [int(line.strip()) for line in output.splitlines() if line.strip().isdigit()]


def build_sql(strategy: str, definition_ids: list[int]) -> Optional[str]:
    return {
        "vertical-join": vertical_join_sql,
        "sequence-window": vertical_join_sql,
        "spliterator-window": vertical_join_sql,
        "imperative-result-set": vertical_join_sql,
        "sql-pivot": lambda: sql_pivot_sql(definition_ids),
        "json-aggregation": json_aggregation_sql,
        "preload-base": preload_base_sql,
        "preload-attributes": preload_attributes_sql,
    }.get(strategy, lambda: None)()


def vertical_join_sql() -> str:
    fields = BASE_FIELDS + [
        "oav.attribute_definition_id AS definition_id",
        "oav.value",
    ]
    return f"""
SELECT
  {select_list(fields)}
{BASE_FROM}
LEFT JOIN order_attribute_value oav ON oav.order_id = o.id
ORDER BY o.id ASC, oav.attribute_definition_id ASC
""".strip()


def sql_pivot_sql(definition_ids: list[int]) -> str:
    pivot_fields = [
        (
            "MAX(CASE WHEN oav.attribute_definition_id = "
            f"{definition_id} THEN oav.value END) AS attr_{definition_id}"
        )
        for definition_id in definition_ids
    ]
    fields = BASE_FIELDS + pivot_fields
    return f"""
SELECT
  {select_list(fields)}
{BASE_FROM}
LEFT JOIN order_attribute_value oav ON oav.order_id = o.id
GROUP BY {BASE_GROUP_BY}
ORDER BY o.id ASC
""".strip()


def json_aggregation_sql() -> str:
    fields = BASE_FIELDS + [
        """
COALESCE(
    jsonb_object_agg(oav.attribute_definition_id, oav.value)
      FILTER (WHERE oav.attribute_definition_id IS NOT NULL AND oav.value IS NOT NULL),
    '{}'::jsonb
) AS attributes
""".strip()
    ]
    return f"""
SELECT
  {select_list(fields)}
{BASE_FROM}
LEFT JOIN order_attribute_value oav ON oav.order_id = o.id
GROUP BY {BASE_GROUP_BY}
ORDER BY o.id ASC
""".strip()


def preload_base_sql() -> str:
    return f"""
SELECT
  {select_list(BASE_FIELDS)}
{BASE_FROM}
ORDER BY o.id ASC
""".strip()


def preload_attributes_sql() -> str:
    return """
SELECT
  oav.order_id,
  oav.attribute_definition_id,
  oav.value
FROM order_attribute_value oav
JOIN "order" o ON o.id = oav.order_id
""".strip()


def explain(sql: str, fmt: str) -> str:
    return f"EXPLAIN (ANALYZE, BUFFERS, FORMAT {fmt})\n{sql}"


def select_list(fields: list[str]) -> str:
    return ",\n  ".join(fields)


def run_psql(args: argparse.Namespace, sql: str, tuples_only: bool = False) -> str:
    command = [
        "docker",
        "compose",
        "exec",
        "-T",
        args.db_service,
        "psql",
        "-X",
        "-v",
        "ON_ERROR_STOP=1",
        "-U",
        args.db_user,
        "-d",
        args.db_name,
        "-P",
        "pager=off",
    ]
    if tuples_only:
        command.extend(["-A", "-t"])
    command.extend(["-c", session_prelude(args) + sql])
    completed = subprocess.run(
        command,
        check=False,
        capture_output=True,
        text=True,
        timeout=args.timeout,
    )
    if completed.returncode != 0:
        message = "\n".join(part for part in [completed.stdout.strip(), completed.stderr.strip()] if part)
        raise SystemExit(message)
    return strip_command_tags(completed.stdout)


def session_prelude(args: argparse.Namespace) -> str:
    if not args.pg_setting:
        return ""
    statements = [setting_to_set_statement(setting) for setting in args.pg_setting]
    return "\n".join(statements) + "\n"


def setting_to_set_statement(setting: str) -> str:
    if "=" not in setting:
        raise SystemExit(f"Invalid --pg-setting value: {setting}. Expected key=value.")
    name, value = [part.strip() for part in setting.split("=", 1)]
    if not name or not value or ";" in name or ";" in value or "\n" in name or "\n" in value:
        raise SystemExit(f"Invalid --pg-setting value: {setting}")
    return f"SET {name} = {value};"


def sanitize_filename(value: str) -> str:
    return "".join(ch if ch.isalnum() or ch in ("-", "_") else "-" for ch in value)


def strip_command_tags(output: str) -> str:
    lines = [line for line in output.splitlines() if line.strip() != "SET"]
    if not lines:
        return ""
    return "\n".join(lines) + "\n"


def summarize_explain_text(
    strategy: str,
    run_number: int,
    setting_label: str,
    path: Path,
    text: str,
) -> dict[str, Any]:
    return {
        "strategy": strategy,
        "run": run_number,
        "settingLabel": setting_label or "default",
        "path": str(path),
        "executionMs": extract_float(text, r"Execution Time: ([0-9.]+) ms"),
        "planningMs": extract_float(text, r"Planning Time: ([0-9.]+) ms"),
        "jitTotalMs": extract_float(text, r"Total ([0-9.]+) ms"),
    }


def extract_float(text: str, pattern: str) -> Optional[float]:
    match = re.search(pattern, text)
    if match is None:
        return None
    return float(match.group(1))


def write_measurements(explain_dir: Path, artifact_suffix: str, measurements: list[dict[str, Any]]) -> None:
    suffix = artifact_suffix or ".default"
    json_path = explain_dir / f"summary{suffix}.json"
    csv_path = explain_dir / f"summary{suffix}.csv"
    json_path.write_text(json.dumps(measurements, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    with csv_path.open("w", encoding="utf-8", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=list(measurements[0].keys()))
        writer.writeheader()
        writer.writerows(measurements)


if __name__ == "__main__":
    sys.exit(main())
