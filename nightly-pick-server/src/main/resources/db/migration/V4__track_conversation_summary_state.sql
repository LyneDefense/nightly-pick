ALTER TABLE conversation_session
    ADD COLUMN user_message_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN summarized_user_message_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN summary_job_status VARCHAR(32) NOT NULL DEFAULT 'idle',
    ADD COLUMN summary_job_target_user_message_count INTEGER NOT NULL DEFAULT 0;

UPDATE conversation_session session
SET user_message_count = COALESCE(counts.user_count, 0)
FROM (
    SELECT session_id, COUNT(*) AS user_count
    FROM conversation_message
    WHERE role = 'user'
    GROUP BY session_id
) counts
WHERE session.id = counts.session_id;

UPDATE conversation_session session
SET summarized_user_message_count = session.user_message_count
WHERE EXISTS (
    SELECT 1
    FROM daily_record record
    WHERE record.session_id = session.id
);
