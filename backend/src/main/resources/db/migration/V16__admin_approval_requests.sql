CREATE TABLE admin_approval_requests (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    type TEXT NOT NULL CHECK (type IN ('WALLET_ADJUSTMENT', 'PAYMENT_REFUND', 'COMPENSATION')),
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    entity_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    payload_json TEXT NOT NULL,
    reason TEXT NOT NULL,
    requested_by INTEGER NOT NULL REFERENCES users(id),
    reviewed_by INTEGER REFERENCES users(id),
    result_entity_type TEXT,
    result_entity_id TEXT,
    created_at TEXT NOT NULL,
    reviewed_at TEXT,
    updated_at TEXT NOT NULL
);

CREATE INDEX idx_admin_approval_requests_status ON admin_approval_requests(status);
CREATE INDEX idx_admin_approval_requests_type ON admin_approval_requests(type);
CREATE INDEX idx_admin_approval_requests_entity ON admin_approval_requests(entity_type, entity_id);
