CREATE TABLE app_user (
    id VARCHAR(64) PRIMARY KEY,
    nickname VARCHAR(255) NOT NULL,
    allow_memory_reference BOOLEAN NOT NULL DEFAULT TRUE,
    created_date DATE NOT NULL
);

CREATE TABLE conversation_session (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at TIMESTAMP WITH TIME ZONE,
    user_message_count INTEGER NOT NULL DEFAULT 0,
    summarized_user_message_count INTEGER NOT NULL DEFAULT 0,
    summary_job_status VARCHAR(32) NOT NULL DEFAULT 'idle',
    summary_job_target_user_message_count INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_conversation_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

CREATE TABLE conversation_message (
    id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL,
    input_type VARCHAR(32) NOT NULL,
    text TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_message_session FOREIGN KEY (session_id) REFERENCES conversation_session (id) ON DELETE CASCADE
);

CREATE INDEX idx_message_session_created_at ON conversation_message (session_id, created_at);

CREATE TABLE daily_record (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    record_date DATE NOT NULL,
    title VARCHAR(255) NOT NULL,
    summary TEXT NOT NULL,
    events_json TEXT NOT NULL,
    emotions_json TEXT NOT NULL,
    open_loops_json TEXT NOT NULL,
    highlight TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_record_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_record_session FOREIGN KEY (session_id) REFERENCES conversation_session (id)
);

CREATE INDEX idx_record_user_created_at ON daily_record (user_id, created_at DESC);

CREATE UNIQUE INDEX uk_daily_record_user_date ON daily_record (user_id, record_date);

CREATE TABLE memory_item (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    type VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    source_record_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    topic_key VARCHAR(128),
    mention_count INTEGER NOT NULL DEFAULT 1,
    importance_score DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    first_seen_at TIMESTAMP WITH TIME ZONE,
    last_seen_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_memory_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_memory_record FOREIGN KEY (source_record_id) REFERENCES daily_record (id) ON DELETE CASCADE
);

CREATE INDEX idx_memory_user_created_at ON memory_item (user_id, created_at DESC);

CREATE INDEX idx_memory_user_type_last_seen ON memory_item (user_id, type, last_seen_at DESC);

CREATE INDEX idx_memory_user_topic_key ON memory_item (user_id, topic_key);
