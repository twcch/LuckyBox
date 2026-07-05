-- 每日登入任務（簽到送點）：記錄每位會員每日一次的簽到與獎勵點數。
CREATE TABLE daily_check_ins (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL REFERENCES users(id),
    check_in_date TEXT NOT NULL,
    reward_amount INTEGER NOT NULL DEFAULT 0 CHECK (reward_amount >= 0),
    point_kind TEXT NOT NULL DEFAULT 'BONUS' CHECK (point_kind IN ('CASH', 'BONUS')),
    created_at TEXT NOT NULL,
    UNIQUE (user_id, check_in_date)
);

CREATE INDEX idx_daily_check_ins_user_date ON daily_check_ins (user_id, check_in_date DESC);
