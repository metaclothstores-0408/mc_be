--liquibase formatted sql
--changeset metacloth:015
ALTER TABLE dtf_design_collections ADD COLUMN IF NOT EXISTS collection_group VARCHAR(100) DEFAULT 'General';
CREATE INDEX idx_dtf_col_group ON dtf_design_collections(collection_group);
