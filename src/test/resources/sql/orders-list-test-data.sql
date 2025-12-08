SET search_path TO test;

-- Insert test customers
INSERT INTO customer (id, name, email, created_at) VALUES
    (1, 'Alice Smith', 'alice@example.com', '2024-01-01 10:00:00'),
    (2, 'Bob Johnson', 'bob@example.com', '2024-01-02 10:00:00'),
    (3, 'Charlie Brown', 'charlie@example.com', '2024-01-03 10:00:00');

-- Insert test orders
INSERT INTO "order" (id, customer_id, order_date, total_amount, created_at) VALUES
    (1, 1, '2024-01-10 10:00:00', 1000.00, '2024-01-10 10:00:00'),
    (2, 2, '2024-01-15 12:00:00', 2500.00, '2024-01-15 12:00:00'),
    (3, 1, '2024-01-20 14:00:00', 500.00, '2024-01-20 14:00:00'),
    (4, 3, '2024-01-25 16:00:00', 3000.00, '2024-01-25 16:00:00'),
    (5, 2, '2024-02-01 10:00:00', 1500.00, '2024-02-01 10:00:00');

-- Insert test order items
INSERT INTO order_item (id, order_id, product_name, quantity, unit_price, created_at) VALUES
    (1, 1, 'Product A', 2, 500.00, '2024-01-10 10:00:00'),
    (2, 2, 'Product B', 1, 2500.00, '2024-01-15 12:00:00'),
    (3, 3, 'Product C', 1, 500.00, '2024-01-20 14:00:00'),
    (4, 4, 'Product D', 3, 1000.00, '2024-01-25 16:00:00'),
    (5, 5, 'Product E', 1, 1500.00, '2024-02-01 10:00:00'),
    (6, 5, 'Product F', 1, 0.00, '2024-02-01 10:00:00');
