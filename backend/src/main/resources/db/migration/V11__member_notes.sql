-- 客服備註：管理員/客服對單一會員留下的內部備註（append-only）。
CREATE TABLE member_notes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL REFERENCES users(id),
    author_id INTEGER NOT NULL REFERENCES users(id),
    content TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE INDEX idx_member_notes_user ON member_notes (user_id, id DESC);
