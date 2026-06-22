-- mock_session: align with MockSession entity
ALTER TABLE mock_session RENAME COLUMN transcript  TO turns_json;
ALTER TABLE mock_session RENAME COLUMN feedback    TO feedback_json;
ALTER TABLE mock_session RENAME COLUMN created_at  TO started_at;
ALTER TABLE mock_session ALTER  COLUMN feedback_json TYPE JSONB USING feedback_json::JSONB;
ALTER TABLE mock_session ADD COLUMN topic_slug   VARCHAR(255);
ALTER TABLE mock_session ADD COLUMN status       VARCHAR(24)  NOT NULL DEFAULT 'in_progress';
ALTER TABLE mock_session ADD COLUMN finished_at  TIMESTAMPTZ;

-- prep_pack: align with PrepPack entity
ALTER TABLE prep_pack RENAME COLUMN topics       TO topics_json;
ALTER TABLE prep_pack RENAME COLUMN generated_at TO created_at;
ALTER TABLE prep_pack ADD COLUMN questions_json JSONB;
ALTER TABLE prep_pack ADD COLUMN tips_json      JSONB;

-- star_story: tagsJson maps to tags_json, SQL has tags
ALTER TABLE star_story RENAME COLUMN tags TO tags_json;

-- resume_profile: add columns needed by ResumeProfile entity
ALTER TABLE resume_profile ADD COLUMN parsed_json    JSONB;
ALTER TABLE resume_profile ADD COLUMN confirmed_json JSONB;
ALTER TABLE resume_profile ADD COLUMN plan_committed BOOLEAN      NOT NULL DEFAULT false;
ALTER TABLE resume_profile ADD COLUMN updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now();
