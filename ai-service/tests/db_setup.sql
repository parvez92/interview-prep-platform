-- Minimal schema for ai-service integration tests.
-- Mirrors the real Flyway migrations (V7-V9, V17) without FK enforcement
-- so we don't need the full backend-core schema.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS app_user (
    id           BIGSERIAL PRIMARY KEY,
    email        VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(255),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS resume_chunk (
    id        BIGSERIAL PRIMARY KEY,
    user_id   BIGINT      NOT NULL,
    text      TEXT        NOT NULL,
    embedding VECTOR(1024)
);

CREATE TABLE IF NOT EXISTS ai_cache (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT        NOT NULL,
    cache_key     VARCHAR(512)  NOT NULL UNIQUE,
    kind          VARCHAR(40)   NOT NULL,
    response_json TEXT          NOT NULL,
    model         VARCHAR(60)   NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS usage_log (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT        NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    feature           VARCHAR(60)   NOT NULL,
    model             VARCHAR(60)   NOT NULL,
    prompt_tokens     INT           NOT NULL DEFAULT 0,
    completion_tokens INT           NOT NULL DEFAULT 0,
    cost_usd          NUMERIC(10,4) NOT NULL DEFAULT 0,
    provider          VARCHAR(60)   NOT NULL DEFAULT 'anthropic'
);

CREATE TABLE IF NOT EXISTS weak_answer_chunk (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    question_id BIGINT      NOT NULL UNIQUE,
    topic_id    BIGINT,
    text        TEXT        NOT NULL,
    embedding   VECTOR(1024),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS topic_chunk (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    slug       VARCHAR(255) NOT NULL,
    title      TEXT         NOT NULL,
    text       TEXT         NOT NULL,
    embedding  VECTOR(1024),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (user_id, slug)
);

CREATE TABLE IF NOT EXISTS agent_run (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL,
    agent          VARCHAR(60)  NOT NULL,
    trigger_ref    VARCHAR(255),
    steps_used     INT          NOT NULL DEFAULT 0,
    tokens_used    INT          NOT NULL DEFAULT 0,
    status         VARCHAR(20)  NOT NULL DEFAULT 'ok',
    result_summary TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO app_user (id, email, password_hash, display_name)
VALUES (1, 'test@preploop.local', 'hash', 'Test User')
ON CONFLICT DO NOTHING;
