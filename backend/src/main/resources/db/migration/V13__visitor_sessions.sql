CREATE TABLE visitor_sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    visitor_id TEXT NOT NULL UNIQUE,
    first_path TEXT,
    last_path TEXT,
    visit_count INTEGER NOT NULL DEFAULT 0 CHECK (visit_count >= 0),
    registered_user_id INTEGER REFERENCES users(id),
    first_seen_at TEXT NOT NULL,
    last_seen_at TEXT NOT NULL,
    registered_at TEXT
);

CREATE INDEX idx_visitor_sessions_registered ON visitor_sessions (registered_at);
CREATE INDEX idx_visitor_sessions_registered_user ON visitor_sessions (registered_user_id);
