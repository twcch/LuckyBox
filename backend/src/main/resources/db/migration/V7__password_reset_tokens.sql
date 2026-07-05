-- Forgot-password / reset flow: one-time, time-limited tokens. Only the SHA-256 hash is stored,
-- the raw token is delivered to the user (dev: logged), so a DB read cannot reuse outstanding tokens.
CREATE TABLE password_reset_tokens (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL REFERENCES users(id),
    token_hash TEXT NOT NULL UNIQUE,
    expires_at TEXT NOT NULL,
    used_at TEXT,
    created_at TEXT NOT NULL
);

CREATE INDEX idx_password_reset_user ON password_reset_tokens(user_id);
