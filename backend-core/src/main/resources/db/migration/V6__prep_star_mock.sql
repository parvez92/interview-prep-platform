CREATE TABLE prep_pack (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    interview_id BIGINT       NOT NULL UNIQUE REFERENCES interview(id) ON DELETE CASCADE,
    topics       JSONB        NOT NULL DEFAULT '[]',
    summary      TEXT,
    generated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE star_story (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT        NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    title           VARCHAR(200)  NOT NULL,
    situation       TEXT,
    task            TEXT,
    action          TEXT,
    result          TEXT,
    tags            JSONB         NOT NULL DEFAULT '[]',
    mapped_prompts  JSONB         NOT NULL DEFAULT '[]',
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE mock_session (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    type         VARCHAR(24)  NOT NULL,
    topic_scope  JSONB        NOT NULL DEFAULT '[]',
    transcript   JSONB        NOT NULL DEFAULT '[]',
    score        INT,
    feedback     TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
