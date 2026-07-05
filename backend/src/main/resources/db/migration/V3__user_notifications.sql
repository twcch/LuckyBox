CREATE TABLE user_notifications (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL REFERENCES users(id),
    type TEXT NOT NULL,
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    link_url TEXT,
    reference_type TEXT,
    reference_id INTEGER,
    read_at TEXT,
    created_at TEXT NOT NULL
);

CREATE INDEX idx_notification_user_created ON user_notifications(user_id, created_at);
CREATE INDEX idx_notification_user_read ON user_notifications(user_id, read_at);
CREATE UNIQUE INDEX idx_notification_reference_once
    ON user_notifications(user_id, type, reference_type, reference_id)
    WHERE reference_type IS NOT NULL AND reference_id IS NOT NULL;
