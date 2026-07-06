#!/usr/bin/env bash
set -euo pipefail

ORDERS="${ORDERS:-${1:-5000}}"
ATTRS="${ATTRS:-${2:-15}}"
SEED="${SEED:-${3:-42}}"
CUSTOMERS="${CUSTOMERS:-$(( ORDERS / 5 ))}"
CUSTOMERS=$(( CUSTOMERS > 0 ? CUSTOMERS : 1 ))
ITEMS_PER_ORDER="${ITEMS_PER_ORDER:-3}"

DB_SERVICE="${DB_SERVICE:-postgres}"
DB_NAME="${DB_NAME:-simple_ec}"
DB_USER="${DB_USER:-postgres}"

echo "=========================================="
echo "Seeding benchmark dataset via PostgreSQL"
echo "=========================================="
echo "  DB service: ${DB_SERVICE}"
echo "  Customers: ${CUSTOMERS}"
echo "  Orders: ${ORDERS}"
echo "  Attributes: ${ATTRS}"
echo "  Items/order: ${ITEMS_PER_ORDER}"
echo "  Seed: ${SEED}"
echo "=========================================="

docker compose exec -T "${DB_SERVICE}" psql -v ON_ERROR_STOP=1 -U "${DB_USER}" -d "${DB_NAME}" \
  -v customers="${CUSTOMERS}" \
  -v orders="${ORDERS}" \
  -v attrs="${ATTRS}" \
  -v items_per_order="${ITEMS_PER_ORDER}" \
  -v seed="${SEED}" <<'SQL'
\timing on

TRUNCATE TABLE
  order_attribute_value,
  order_item,
  "order",
  customer,
  order_attribute_definition
RESTART IDENTITY CASCADE;

INSERT INTO customer (id, name, email, created_at)
SELECT
  i,
  '顧客' || i,
  'customer' || i || '@example.com',
  timestamp '2026-01-01 00:00:00'
FROM generate_series(1, :customers) AS s(i);

INSERT INTO order_attribute_definition (id, name, label, description, created_at)
SELECT
  i,
  'attr_' || i,
  '属性' || i,
  NULL,
  timestamp '2026-01-01 00:00:00'
FROM generate_series(1, :attrs) AS s(i);

INSERT INTO "order" (id, customer_id, order_date, total_amount, created_at)
SELECT
  i,
  ((i - 1) % :customers) + 1,
  timestamp '2026-01-01 00:00:00'
    + (((i + :seed) % 30) * interval '1 day')
    + ((i % 24) * interval '1 hour'),
  (((i % 20) + 1) * 1500)::numeric(12, 2),
  timestamp '2026-01-01 00:00:00'
FROM generate_series(1, :orders) AS s(i);

INSERT INTO order_item (order_id, product_name, quantity, unit_price, created_at)
SELECT
  o.i,
  '商品' || (((o.i + item.i + :seed) % 100) + 1),
  ((o.i + item.i) % 5) + 1,
  ((((o.i + item.i) % 20) + 1) * 500)::numeric(12, 2),
  timestamp '2026-01-01 00:00:00'
FROM generate_series(1, :orders) AS o(i)
CROSS JOIN generate_series(1, :items_per_order) AS item(i);

INSERT INTO order_attribute_value (order_id, attribute_definition_id, value, created_at)
SELECT
  o.i,
  d.i,
  'v' || d.i || '_' || (o.i % 10),
  timestamp '2026-01-01 00:00:00'
FROM generate_series(1, :orders) AS o(i)
CROSS JOIN generate_series(1, :attrs) AS d(i);

SELECT setval('customer_id_seq', COALESCE((SELECT max(id) FROM customer), 1), true);
SELECT setval('order_id_seq', COALESCE((SELECT max(id) FROM "order"), 1), true);
SELECT setval('order_item_id_seq', COALESCE((SELECT max(id) FROM order_item), 1), true);
SELECT setval(
  'order_attribute_definition_id_seq',
  COALESCE((SELECT max(id) FROM order_attribute_definition), 1),
  true
);
SELECT setval(
  'order_attribute_value_id_seq',
  COALESCE((SELECT max(id) FROM order_attribute_value), 1),
  true
);

SELECT
  (SELECT count(*) FROM customer) AS customers,
  (SELECT count(*) FROM "order") AS orders,
  (SELECT count(*) FROM order_item) AS order_items,
  (SELECT count(*) FROM order_attribute_definition) AS attribute_definitions,
  (SELECT count(*) FROM order_attribute_value) AS attribute_values;
SQL

echo ""
echo "Done."
