-- AI-generated topic titles are deliberately specific and can exceed the
-- original sizes; slug (derived from title) at 80 chars broke plan commit.
ALTER TABLE topic ALTER COLUMN slug TYPE VARCHAR(255);
ALTER TABLE topic ALTER COLUMN title TYPE VARCHAR(255);
