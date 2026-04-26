UPDATE resource_type_config
SET embed_policy = 'ALLOW_ALL'
WHERE embed_policy IS NULL OR embed_policy = 'DISABLED';

ALTER TABLE resource_type_config
    ALTER COLUMN embed_policy SET DEFAULT 'ALLOW_ALL';
