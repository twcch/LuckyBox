CREATE TABLE payment_webhook_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    provider TEXT NOT NULL,
    event_id TEXT NOT NULL,
    merchant_trade_no TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('PAID', 'FAILED', 'CANCELED')),
    amount INTEGER NOT NULL CHECK (amount >= 0),
    raw_payload TEXT NOT NULL,
    processed INTEGER NOT NULL DEFAULT 0 CHECK (processed IN (0, 1)),
    message TEXT,
    created_at TEXT NOT NULL,
    processed_at TEXT,
    UNIQUE(provider, event_id)
);

CREATE INDEX idx_payment_webhook_events_order ON payment_webhook_events (provider, merchant_trade_no);
