ALTER TABLE outage ALTER COLUMN resource_id DROP NOT NULL;

ALTER TABLE resource_type_config
    ADD COLUMN IF NOT EXISTS delete_outages_on_resource_delete BOOLEAN NOT NULL DEFAULT TRUE;
