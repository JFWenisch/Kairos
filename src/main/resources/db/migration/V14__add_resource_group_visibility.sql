ALTER TABLE resource_group
    ADD COLUMN IF NOT EXISTS visibility VARCHAR(32) NOT NULL DEFAULT 'PUBLIC';

UPDATE resource_group
SET visibility = 'PUBLIC'
WHERE visibility IS NULL OR visibility = '';