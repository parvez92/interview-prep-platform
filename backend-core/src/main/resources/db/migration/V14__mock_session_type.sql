-- V10 aligned mock_session with the entity but left V6 leftovers unmapped:
-- `type` (NOT NULL, broke every insert) and `topic_scope`/`score` (dead).
-- The entity now maps `type`; score lives inside feedback_json.
ALTER TABLE mock_session ALTER COLUMN type SET DEFAULT 'technical';
ALTER TABLE mock_session DROP COLUMN topic_scope;
ALTER TABLE mock_session DROP COLUMN score;
ALTER TABLE mock_session ALTER COLUMN topic_slug DROP NOT NULL;
