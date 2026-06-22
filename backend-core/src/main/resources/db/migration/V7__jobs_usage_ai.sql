CREATE TABLE job_alert (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT         NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    source        VARCHAR(60),
    company       VARCHAR(160)   NOT NULL,
    role          VARCHAR(200)   NOT NULL,
    location      VARCHAR(255),
    comp          VARCHAR(255),
    url           VARCHAR(2000),
    jd_text       TEXT,
    email_msg_id  VARCHAR(255)   UNIQUE,
    received_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    fit_score     INT,
    fit_reason    TEXT,
    status        VARCHAR(16)    NOT NULL DEFAULT 'new'
);

CREATE TABLE usage_log (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT        NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    ts         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    source     VARCHAR(60)   NOT NULL,
    model      VARCHAR(60)   NOT NULL,
    tokens_in  INT           NOT NULL DEFAULT 0,
    tokens_out INT           NOT NULL DEFAULT 0,
    cost_usd   NUMERIC(10,4) NOT NULL DEFAULT 0
);

CREATE TABLE ai_cache (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT        NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    cache_key  VARCHAR(512)  NOT NULL UNIQUE,
    kind       VARCHAR(40)   NOT NULL,
    content    TEXT          NOT NULL,
    model      VARCHAR(60)   NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE agent_run (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT        NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    agent          VARCHAR(60)   NOT NULL,
    trigger_ref    VARCHAR(255),
    steps_used     INT           NOT NULL DEFAULT 0,
    tokens_used    INT           NOT NULL DEFAULT 0,
    status         VARCHAR(20)   NOT NULL DEFAULT 'ok',
    result_summary TEXT,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);
