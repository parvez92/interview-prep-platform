-- agent_run: align column names with entity fields
ALTER TABLE agent_run RENAME COLUMN agent      TO agent_name;
ALTER TABLE agent_run RENAME COLUMN created_at TO started_at;
ALTER TABLE agent_run RENAME COLUMN steps_used TO steps;
ALTER TABLE agent_run ADD COLUMN input_json  JSONB;
ALTER TABLE agent_run ADD COLUMN output_json JSONB;
ALTER TABLE agent_run ADD COLUMN finished_at TIMESTAMPTZ;

-- usage_log: align column names with entity fields
ALTER TABLE usage_log RENAME COLUMN source     TO feature;
ALTER TABLE usage_log RENAME COLUMN tokens_in  TO prompt_tokens;
ALTER TABLE usage_log RENAME COLUMN tokens_out TO completion_tokens;
ALTER TABLE usage_log RENAME COLUMN ts         TO created_at;
ALTER TABLE usage_log ADD COLUMN provider VARCHAR(60) NOT NULL DEFAULT 'anthropic';

-- ai_cache: entity calls the text column response_json
ALTER TABLE ai_cache RENAME COLUMN content TO response_json;

-- job_alert: align column names and types with entity fields
ALTER TABLE job_alert RENAME COLUMN received_at TO created_at;
ALTER TABLE job_alert RENAME COLUMN fit_score   TO match_score;
ALTER TABLE job_alert ALTER  COLUMN match_score TYPE DOUBLE PRECISION USING match_score::DOUBLE PRECISION;
ALTER TABLE job_alert ADD COLUMN meta_json JSONB;
