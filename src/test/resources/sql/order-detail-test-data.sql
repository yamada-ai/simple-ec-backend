-- Test data for order detail API test

-- Insert test customer
INSERT INTO customer (id, name, email, created_at) VALUES
    (1, 'Test Customer', 'test@example.com', '2024-01-01 10:00:00');

-- Insert test order
INSERT INTO "order" (id, customer_id, order_date, total_amount, created_at) VALUES
    (1, 1, '2024-01-15 14:30:00', 3500.00, '2024-01-15 14:30:00');

-- Insert test order items
INSERT INTO order_item (id, order_id, product_name, quantity, unit_price, created_at) VALUES
    (1, 1, 'Laptop', 1, 2500.00, '2024-01-15 14:30:00'),
    (2, 1, 'Mouse', 2, 500.00, '2024-01-15 14:30:00');
