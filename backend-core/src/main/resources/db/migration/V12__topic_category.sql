-- Plan-assigned category (dsa|system_design|behavioral|language|framework|cloud|domain).
-- Drives type-specific AI generation for guide tabs; distinct from the UI badge `tag`.
ALTER TABLE topic ADD COLUMN category VARCHAR(40);
