--liquibase formatted sql
--changeset metacloth:012
ALTER TABLE dtf_production_assets ADD COLUMN IF NOT EXISTS selected_sheet_preset VARCHAR(10) DEFAULT 'A3';
ALTER TABLE dtf_production_assets ADD COLUMN IF NOT EXISTS print_width_inches DOUBLE PRECISION;
ALTER TABLE dtf_production_assets ADD COLUMN IF NOT EXISTS sheet_width_inches DOUBLE PRECISION;
ALTER TABLE dtf_production_assets ADD COLUMN IF NOT EXISTS sheet_height_inches DOUBLE PRECISION;
