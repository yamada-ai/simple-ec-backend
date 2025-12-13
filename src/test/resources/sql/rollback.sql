SET search_path TO test;

DELETE FROM order_attribute_value;
DELETE FROM order_item;
DELETE FROM "order";
DELETE FROM order_attribute_definition;
DELETE FROM customer;

-- Reset sequences to ensure reproducible IDs
SELECT setval('customer_id_seq', 1, false);
SELECT setval('order_id_seq', 1, false);
SELECT setval('order_item_id_seq', 1, false);
SELECT setval('order_attribute_definition_id_seq', 1, false);
SELECT setval('order_attribute_value_id_seq', 1, false);
