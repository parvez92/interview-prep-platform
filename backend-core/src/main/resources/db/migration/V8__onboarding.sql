CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE resume_profile (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT        NOT NULL UNIQUE REFERENCES app_user(id) ON DELETE CASCADE,
    raw_text    TEXT,
    file_ref    VARCHAR(500),
    skills      JSONB         NOT NULL DEFAULT '[]',
    domains     JSONB         NOT NULL DEFAULT '[]',
    seniority   VARCHAR(40),
    total_years NUMERIC(4,1),
    experiences JSONB         NOT NULL DEFAULT '[]',
    gaps        JSONB         NOT NULL DEFAULT '[]',
    confirmed   BOOLEAN       NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- resume_chunk is owned/written by Python (pgvector) but we declare it here
-- so Flyway owns the full schema and the Python service can insert
CREATE TABLE resume_chunk (
    id        BIGSERIAL PRIMARY KEY,
    user_id   BIGINT   NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    text      TEXT     NOT NULL,
    embedding VECTOR(1024)
);
