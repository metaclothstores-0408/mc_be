--liquibase formatted sql
--changeset metacloth:014
CREATE TABLE dtf_design_collections (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
  session_id VARCHAR(100),
  design_name VARCHAR(255) NOT NULL,
  original_filename VARCHAR(255) NOT NULL,
  version_number INT NOT NULL DEFAULT 1,
  image_data_url TEXT NOT NULL,
  preview_data_url TEXT,
  selected_sheet_preset VARCHAR(10) DEFAULT 'A3',
  print_scale DOUBLE PRECISION DEFAULT 1.0,
  strip_color_hex VARCHAR(7) DEFAULT '#FFFFFF',
  strip_sensitivity INT DEFAULT 30,
  halftone_density INT DEFAULT 0,
  garment_type VARCHAR(50) DEFAULT 'OVERSIZED_BOXY',
  fabric_color_name VARCHAR(50) DEFAULT 'Noir Black',
  fabric_color_hex VARCHAR(7) DEFAULT '#0B0B0C',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_dtf_col_user ON dtf_design_collections(user_id);
CREATE INDEX idx_dtf_col_session ON dtf_design_collections(session_id);
CREATE INDEX idx_dtf_col_updated ON dtf_design_collections(updated_at DESC);

