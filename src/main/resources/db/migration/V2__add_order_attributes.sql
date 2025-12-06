-- Add order attributes (user-defined dynamic fields)
-- This is the core feature for demonstrating dynamic column pivoting in CSV export

-- Order attribute definition table
-- Stores definitions of user-defined attributes (e.g., gift wrapping type, campaign ID, delivery instructions)
CREATE TABLE order_attribute_definition (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    label VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Order attribute value table
-- Stores actual values for each order's attributes
-- Structure: (order_id, attribute_definition_id, value)
-- This creates a sparse matrix that needs to be pivoted into columns for CSV export
CREATE TABLE order_attribute_value (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES "order"(id) ON DELETE CASCADE,
    attribute_definition_id BIGINT NOT NULL REFERENCES order_attribute_definition(id) ON DELETE CASCADE,
    value VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- One value per order per attribute
    CONSTRAINT unique_order_attribute UNIQUE (order_id, attribute_definition_id)
);

-- Indexes for efficient lookup
CREATE INDEX idx_order_attribute_value_order_id ON order_attribute_value(order_id);
CREATE INDEX idx_order_attribute_value_attribute_id ON order_attribute_value(attribute_definition_id);
