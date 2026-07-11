-- Plan amendment 01 §1: persist a topic's plan priority (high|medium|low).
-- Drives skip semantics and the behind-pace Today Queue exclusion.
-- Existing rows and future untagged topics default to 'high' (the interview core).
ALTER TABLE topic ADD COLUMN priority varchar(8) NOT NULL DEFAULT 'high';
