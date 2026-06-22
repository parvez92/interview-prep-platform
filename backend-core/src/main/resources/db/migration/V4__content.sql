CREATE TABLE note (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    topic_id   BIGINT      NOT NULL UNIQUE REFERENCES topic(id) ON DELETE CASCADE,
    content_md TEXT        NOT NULL DEFAULT '',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE attachment (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT        NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    note_id       BIGINT        NOT NULL REFERENCES note(id) ON DELETE CASCADE,
    file_ref      VARCHAR(500)  NOT NULL,
    original_name VARCHAR(255)  NOT NULL,
    mime_type     VARCHAR(100)  NOT NULL,
    size_bytes    BIGINT        NOT NULL
);

CREATE TABLE resource (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT        NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    topic_id      BIGINT        NOT NULL REFERENCES topic(id) ON DELETE CASCADE,
    label         VARCHAR(255)  NOT NULL,
    url           VARCHAR(2000) NOT NULL,
    display_order INT           NOT NULL DEFAULT 0
);

CREATE TABLE exercise (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT        NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    topic_id      BIGINT        NOT NULL REFERENCES topic(id) ON DELETE CASCADE,
    title         VARCHAR(255)  NOT NULL,
    repo_url      VARCHAR(2000),
    done          BOOLEAN       NOT NULL DEFAULT false,
    display_order INT           NOT NULL DEFAULT 0
);

CREATE TABLE question (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    topic_id      BIGINT       NOT NULL REFERENCES topic(id) ON DELETE CASCADE,
    text          TEXT         NOT NULL,
    display_order INT          NOT NULL DEFAULT 0
);
