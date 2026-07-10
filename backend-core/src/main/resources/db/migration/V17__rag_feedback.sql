-- RAG feedback tables. Like resume_chunk (V8), these are owned/written by the
-- Python ai-service but declared here so Flyway owns the full schema.

-- One row per weakly-answered interview question (self-rating <= 2), embedded so
-- guide generation can retrieve what the candidate actually fumbled — including
-- semantically similar questions logged under other topics.
CREATE TABLE weak_answer_chunk (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    question_id BIGINT      NOT NULL UNIQUE,
    topic_id    BIGINT,
    text        TEXT        NOT NULL,
    embedding   VECTOR(1024),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_weak_answer_chunk_user ON weak_answer_chunk(user_id);

-- One row per generated deep-dive card (Pass 3), embedded so later depth batches
-- can see what adjacent topics already cover instead of repeating it.
CREATE TABLE topic_chunk (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    slug       VARCHAR(255) NOT NULL,
    title      TEXT        NOT NULL,
    text       TEXT        NOT NULL,
    embedding  VECTOR(1024),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, slug)
);