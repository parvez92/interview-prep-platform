ALTER TABLE user_settings
    RENAME COLUMN model_strong TO llm_model_strong;
ALTER TABLE user_settings
    RENAME COLUMN model_cheap TO llm_model_cheap;
ALTER TABLE user_settings
    ADD COLUMN IF NOT EXISTS target_role       VARCHAR(160),
    ADD COLUMN IF NOT EXISTS target_level      VARCHAR(40),
    ADD COLUMN IF NOT EXISTS prep_weeks        INT,
    ADD COLUMN IF NOT EXISTS hours_per_week    INT,
    ADD COLUMN IF NOT EXISTS interests         JSONB NOT NULL DEFAULT '[]';
