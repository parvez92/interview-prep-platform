CREATE TABLE phase (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT        NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    code          VARCHAR(40)   NOT NULL,
    name          VARCHAR(200)  NOT NULL,
    icon          VARCHAR(40),
    blurb         TEXT,
    display_order INT           NOT NULL DEFAULT 0,
    UNIQUE (user_id, code)
);

CREATE TABLE week (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT        NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    phase_id      BIGINT        NOT NULL REFERENCES phase(id) ON DELETE CASCADE,
    code          VARCHAR(40)   NOT NULL,
    title         VARCHAR(200)  NOT NULL,
    display_order INT           NOT NULL DEFAULT 0,
    UNIQUE (user_id, code)
);

CREATE TABLE topic (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT        NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    week_id          BIGINT        NOT NULL REFERENCES week(id) ON DELETE CASCADE,
    code             VARCHAR(40)   NOT NULL,
    slug             VARCHAR(80)   NOT NULL,
    title            VARCHAR(200)  NOT NULL,
    tag              VARCHAR(16)   NOT NULL DEFAULT 'new',
    source           VARCHAR(16)   NOT NULL DEFAULT 'standard',
    concept          TEXT,
    points           JSONB         NOT NULL DEFAULT '[]',
    angle            TEXT,
    status           VARCHAR(12)   NOT NULL DEFAULT 'todo',
    is_custom        BOOLEAN       NOT NULL DEFAULT false,
    confidence       INT,
    last_reviewed_at TIMESTAMPTZ,
    display_order    INT           NOT NULL DEFAULT 0,
    UNIQUE (user_id, slug)
);
