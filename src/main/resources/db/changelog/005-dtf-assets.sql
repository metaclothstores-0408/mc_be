--liquibase formatted sql
--changeset metacloth:010
CREATE TABLE dtf_production_assets (
  id BIGSERIAL PRIMARY KEY,
  order_item_id BIGINT NOT NULL REFERENCES order_items(id) ON DELETE CASCADE,
  original_asset_url VARCHAR(1000),
  processed_file_path VARCHAR(1000),
  strip_color_hex VARCHAR(7),
  strip_sensitivity INT,
  halftone_density INT,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  error_message VARCHAR(1000),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_dtf_order_item ON dtf_production_assets(order_item_id);
