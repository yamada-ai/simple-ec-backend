-- Rollback script to clean all test data
-- Delete in reverse FK dependency order

DELETE FROM order_attribute_value;
DELETE FROM order_item;
DELETE FROM "order";
DELETE FROM order_attribute_definition;
DELETE FROM customer;
