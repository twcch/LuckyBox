-- 許願牆 MVP：會員可投稿希望上架的 IP / 系列，公開牆顯示已核准且作者匿名的願望，管理員可審核。
CREATE TABLE wishes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL REFERENCES users(id),
    content TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'HIDDEN')),
    moderator_note TEXT,
    moderated_by INTEGER REFERENCES users(id),
    moderated_at TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE INDEX idx_wishes_status ON wishes (status, id DESC);
CREATE INDEX idx_wishes_user ON wishes (user_id, id DESC);
