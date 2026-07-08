-- Content pipeline v2 (docs/content-pipeline-v2.md):
-- narrative pass columns on week, depth-pass metadata on topic,
-- effort estimates and question typing for the today queue / mock sampling.
ALTER TABLE week ADD COLUMN builds_on JSONB;
ALTER TABLE week ADD COLUMN unlocks   TEXT;
ALTER TABLE week ADD COLUMN bridge    TEXT;
ALTER TABLE week ADD COLUMN anchor    TEXT;

ALTER TABLE topic ADD COLUMN est_minutes   INT;
ALTER TABLE topic ADD COLUMN needs_review  BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE topic ADD COLUMN coarse_parent VARCHAR(255);
ALTER TABLE topic ADD COLUMN split_hint    VARCHAR(8);

ALTER TABLE exercise ADD COLUMN est_minutes INT;
ALTER TABLE question ADD COLUMN type        VARCHAR(16);
