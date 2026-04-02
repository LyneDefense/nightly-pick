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

CREATE TABLE memory_item (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    type VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    source_record_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_memory_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_memory_record FOREIGN KEY (source_record_id) REFERENCES daily_record (id) ON DELETE CASCADE
);

CREATE INDEX idx_memory_user_created_at ON memory_item (user_id, created_at DESC);
