-- product_routing drop routing_type
ALTER TABLE product_routing DROP COLUMN routing_type;

-- product_routing_item add process_category (dict: process_category)
ALTER TABLE product_routing_item ADD COLUMN process_category VARCHAR(50) NULL COMMENT 'process category';
