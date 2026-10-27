--liquibase formatted sql
--changeset metacloth:016
ALTER TABLE dtf_design_collections ALTER COLUMN image_data_url DROP NOT NULL;
ALTER TABLE dtf_design_collections ADD COLUMN IF NOT EXISTS r2_object_key VARCHAR(500);
ALTER TABLE dtf_design_collections ADD COLUMN IF NOT EXISTS r2_image_url VARCHAR(1000);
ALTER TABLE dtf_design_collections ADD COLUMN IF NOT EXISTS r2_preview_key VARCHAR(500);
ALTER TABLE dtf_design_collections ADD COLUMN IF NOT EXISTS r2_preview_url VARCHAR(1000);
ALTER TABLE dtf_design_collections ADD COLUMN IF NOT EXISTS file_size_bytes BIGINT DEFAULT 0;
ALTER TABLE dtf_design_collections ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_dtf_col_expires ON dtf_design_collections(expires_at);
CREATE INDEX IF NOT EXISTS idx_dtf_col_user_size ON dtf_design_collections(user_id, file_size_bytes);

ALTER TABLE dtf_production_assets ADD COLUMN IF NOT EXISTS r2_object_key VARCHAR(500);
ALTER TABLE dtf_production_assets ADD COLUMN IF NOT EXISTS r2_image_url VARCHAR(1000);
ALTER TABLE dtf_production_assets ADD COLUMN IF NOT EXISTS file_size_bytes BIGINT DEFAULT 0;
ALTER TABLE dtf_production_assets ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_dtf_prod_expires ON dtf_production_assets(expires_at);
