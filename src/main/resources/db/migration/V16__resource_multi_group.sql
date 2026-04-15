-- V16: Replace single resource_group_id FK on monitored_resource with a join table
-- allowing a resource to belong to zero, one, or many groups.

CREATE TABLE resource_group_resource (
    resource_id       BIGINT NOT NULL,
    resource_group_id BIGINT NOT NULL,
    CONSTRAINT pk_resource_group_resource PRIMARY KEY (resource_id, resource_group_id),
    CONSTRAINT fk_rgr_resource FOREIGN KEY (resource_id)       REFERENCES monitored_resource (id),
    CONSTRAINT fk_rgr_group    FOREIGN KEY (resource_group_id) REFERENCES resource_group (id)
);

-- Migrate existing single-group assignments into the join table
INSERT INTO resource_group_resource (resource_id, resource_group_id)
SELECT id, resource_group_id
FROM monitored_resource
WHERE resource_group_id IS NOT NULL;

-- Remove the now-redundant column
ALTER TABLE monitored_resource DROP COLUMN IF EXISTS resource_group_id;
