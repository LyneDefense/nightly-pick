DELETE FROM daily_record
WHERE id IN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY user_id, record_date
                   ORDER BY created_at DESC, id DESC
               ) AS row_num
        FROM daily_record
    ) ranked
    WHERE ranked.row_num > 1
);

CREATE UNIQUE INDEX uk_daily_record_user_date
    ON daily_record (user_id, record_date);
