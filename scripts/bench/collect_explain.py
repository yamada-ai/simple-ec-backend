#!/usr/bin/env python3
"""Collect PostgreSQL EXPLAIN artifacts for benchmark query shapes."""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path
from typing import Optional


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

    definition_ids = fetch_definition_ids(args)
    strategies = parse_strategies(args.strategies)

    print("=========================================")
    print("EXPLAIN Artifact Collector")
    print("=========================================")
    print(f"Run dir: {args.run_dir}")
    print(f"Strategies: {', '.join(strategies)}")
    print(f"Attribute definitions: {len(definition_ids)}")
    print("=========================================")

    for strategy in strategies:
        sql = build_sql(strategy, definition_ids)
        if sql is None:
            print(f"Skipping unknown strategy: {strategy}")
            continue

        sql_path = explain_dir / f"{strategy}.sql"
        json_path = explain_dir / f"{strategy}.explain.json"
        text_path = explain_dir / f"{strategy}.explain.txt"
        sql_path.write_text(sql + "\n", encoding="utf-8")

        print(f"Collecting {strategy}...")
        json_explain = run_psql(args, explain(sql, "JSON"))
        json_path.write_text(json_explain, encoding="utf-8")
        text_explain = run_psql(args, explain(sql, "TEXT"))
        text_path.write_text(text_explain, encoding="utf-8")

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
    return parser.parse_args()


def parse_strategies(raw: str) -> list[str]:
    return [item.strip() for item in raw.replace(" ", ",").split(",") if item.strip()]


def fetch_definition_ids(args: argparse.Namespace) -> list[int]:
    output = run_psql(
        args,
        "SELECT id FROM order_attribute_definition ORDER BY id",
        tuples_only=True,
    )
    return [int(line.strip()) for line in output.splitlines() if line.strip()]


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
    command.extend(["-c", sql])
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
    return completed.stdout


if __name__ == "__main__":
    sys.exit(main())
