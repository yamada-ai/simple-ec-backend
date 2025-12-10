SET search_path TO test;

-- customers
INSERT INTO customer (id, name, email, created_at) VALUES
    (1, 'Customer 1', 'c1@example.com', '2024-01-01 00:00:00'),
    (2, 'Customer 2', 'c2@example.com', '2024-01-02 00:00:00');

-- orders
INSERT INTO "order" (id, customer_id, order_date, total_amount, created_at) VALUES
    (10, 1, '2024-02-01 10:00:00', 1000.00, '2024-02-01 10:00:00'),
    (11, 2, '2024-02-02 12:00:00', 2000.00, '2024-02-02 12:00:00');

-- order items (not used in CSV but needed for FK integrity)
INSERT INTO order_item (id, order_id, product_name, quantity, unit_price, created_at) VALUES
    (100, 10, 'Item A', 1, 1000.00, '2024-02-01 10:00:00'),
    (101, 11, 'Item B', 2, 1000.00, '2024-02-02 12:00:00');

-- attribute definitions
INSERT INTO order_attribute_definition (id, name, label, description, created_at) VALUES
    (1, 'gift_wrapping', 'ギフト包装', NULL, '2024-01-01 00:00:00'),
    (2, 'delivery_note', '配送指示', NULL, '2024-01-01 00:00:00');

-- attribute values
INSERT INTO order_attribute_value (id, order_id, attribute_definition_id, value, created_at) VALUES
    (1000, 10, 1, 'あり', '2024-02-01 10:00:00'),
    (1001, 10, 2, '置き配希望', '2024-02-01 10:00:00'),
    (1002, 11, 1, 'なし', '2024-02-02 12:00:00');

SELECT setval('order_attribute_definition_id_seq', 2);
SELECT setval('order_attribute_value_id_seq', 1002);
SELECT setval('order_id_seq', 11);
SELECT setval('customer_id_seq', 2);
SELECT setval('order_item_id_seq', 101);
