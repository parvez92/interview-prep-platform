ALTER TABLE user_settings
    ADD COLUMN IF NOT EXISTS ollama_url VARCHAR(255);
