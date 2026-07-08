-- Distinguish user-entered rows from AI-generated ones so regeneration can
-- replace stale AI content without touching manual entries.
ALTER TABLE resource ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'manual';
ALTER TABLE exercise ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'manual';
ALTER TABLE question ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'manual';
