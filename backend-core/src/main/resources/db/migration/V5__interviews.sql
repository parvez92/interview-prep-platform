CREATE TABLE interview (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT        NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    company       VARCHAR(160)  NOT NULL,
    role          VARCHAR(200)  NOT NULL,
    stage         VARCHAR(16)   NOT NULL DEFAULT 'applied',
    round         VARCHAR(120),
    scheduled_at  DATE,
    outcome       VARCHAR(16)   NOT NULL DEFAULT 'pending',
    jd_text       TEXT,
    job_alert_id  BIGINT,
    notes         TEXT,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE interview_question (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT   NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    interview_id BIGINT   NOT NULL REFERENCES interview(id) ON DELETE CASCADE,
    topic_id     BIGINT   REFERENCES topic(id) ON DELETE SET NULL,
    text         TEXT     NOT NULL,
    self_rating  INT      NOT NULL CHECK (self_rating BETWEEN 1 AND 5)
);

CREATE TABLE review_flag (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    topic_id   BIGINT       NOT NULL REFERENCES topic(id) ON DELETE CASCADE,
    source     VARCHAR(16)  NOT NULL,
    reason     TEXT         NOT NULL,
    resolved   BOOLEAN      NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
