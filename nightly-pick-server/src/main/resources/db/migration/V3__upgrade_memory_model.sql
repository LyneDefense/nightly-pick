ALTER TABLE memory_item
    ADD COLUMN topic_key VARCHAR(128);

ALTER TABLE memory_item
    ADD COLUMN mention_count INTEGER NOT NULL DEFAULT 1;

ALTER TABLE memory_item
    ADD COLUMN importance_score DOUBLE PRECISION NOT NULL DEFAULT 1.0;

ALTER TABLE memory_item
    ADD COLUMN first_seen_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE memory_item
    ADD COLUMN last_seen_at TIMESTAMP WITH TIME ZONE;

UPDATE memory_item
SET topic_key = COALESCE(topic_key, SUBSTRING(REGEXP_REPLACE(LOWER(content), '[[:space:][:punct:]]+', '', 'g') FROM 1 FOR 128)),
    first_seen_at = COALESCE(first_seen_at, created_at),
    last_seen_at = COALESCE(last_seen_at, created_at),
    mention_count = COALESCE(mention_count, 1),
    importance_score = COALESCE(importance_score, 1.0);

CREATE INDEX idx_memory_user_type_last_seen
    ON memory_item (user_id, type, last_seen_at DESC);

CREATE INDEX idx_memory_user_topic_key
    ON memory_item (user_id, topic_key);
