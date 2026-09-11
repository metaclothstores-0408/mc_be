--liquibase formatted sql
--changeset metacloth:011
ALTER TABLE dtf_production_assets ADD COLUMN IF NOT EXISTS print_scale DOUBLE PRECISION DEFAULT 1.0;
