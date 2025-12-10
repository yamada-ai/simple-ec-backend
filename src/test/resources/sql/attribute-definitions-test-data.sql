SET search_path TO test;

-- Insert test attribute definitions
INSERT INTO order_attribute_definition (id, name, label, description, created_at)
VALUES
    (1, 'gift_wrapping', 'ギフト包装', 'ギフト包装の種類を指定', '2024-01-01 00:00:00'),
    (2, 'delivery_time', '配送時間指定', NULL, '2024-01-02 00:00:00'),
    (3, 'campaign_code', 'キャンペーンコード', '適用されたキャンペーンコード', '2024-01-03 00:00:00');

-- Reset sequence
SELECT setval('order_attribute_definition_id_seq', 3);
