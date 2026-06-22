CREATE TABLE app_user (
    id          BIGSERIAL PRIMARY KEY,
    email       VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_settings (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT NOT NULL UNIQUE REFERENCES app_user(id) ON DELETE CASCADE,
    llm_provider       VARCHAR(50)     NOT NULL DEFAULT 'anthropic',
    model_strong       VARCHAR(100),
    model_cheap        VARCHAR(100),
    monthly_budget_usd NUMERIC(10, 2)  NOT NULL DEFAULT 20.00,
    onboarded          BOOLEAN         NOT NULL DEFAULT false
);
