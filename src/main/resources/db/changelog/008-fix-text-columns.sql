--liquibase formatted sql
--changeset metacloth:013
ALTER TABLE order_items ALTER COLUMN preview_render_url TYPE TEXT;
ALTER TABLE dtf_production_assets ALTER COLUMN original_asset_url TYPE TEXT;

