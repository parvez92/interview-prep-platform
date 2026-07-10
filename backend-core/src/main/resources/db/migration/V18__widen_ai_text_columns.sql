-- AI-authored display text (titles, labels, names) regularly exceeds VARCHAR(255)
-- now that stronger models write scenario-style content. TEXT has no storage or
-- performance penalty in Postgres. Identifier/enum columns keep their limits.

ALTER TABLE exercise ALTER COLUMN title TYPE TEXT;
ALTER TABLE resource ALTER COLUMN label TYPE TEXT;
ALTER TABLE topic    ALTER COLUMN title TYPE TEXT;
ALTER TABLE week     ALTER COLUMN title TYPE TEXT;
ALTER TABLE phase    ALTER COLUMN name  TYPE TEXT;
