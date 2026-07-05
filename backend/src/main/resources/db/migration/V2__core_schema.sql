CREATE TABLE users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    email TEXT NOT NULL UNIQUE,
    phone TEXT,
    password_hash TEXT NOT NULL,
    display_name TEXT NOT NULL,
    avatar_url TEXT,
    role TEXT NOT NULL CHECK (role IN ('USER', 'CUSTOMER_SERVICE', 'OPERATOR', 'ADMIN', 'SUPER_ADMIN')),
    status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),
    vip_level TEXT NOT NULL DEFAULT 'REGULAR',
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    last_login_at TEXT
);

CREATE TABLE user_addresses (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL REFERENCES users(id),
    recipient_name TEXT NOT NULL,
    phone TEXT NOT NULL,
    postal_code TEXT,
    city TEXT NOT NULL,
    district TEXT NOT NULL,
    address_line TEXT NOT NULL,
    is_default INTEGER NOT NULL DEFAULT 0 CHECK (is_default IN (0, 1)),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE wallets (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL UNIQUE REFERENCES users(id),
    cash_point_balance INTEGER NOT NULL DEFAULT 0 CHECK (cash_point_balance >= 0),
    bonus_point_balance INTEGER NOT NULL DEFAULT 0 CHECK (bonus_point_balance >= 0),
    locked_balance INTEGER NOT NULL DEFAULT 0 CHECK (locked_balance >= 0),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE wallet_ledger (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL REFERENCES users(id),
    wallet_id INTEGER NOT NULL REFERENCES wallets(id),
    type TEXT NOT NULL,
    amount INTEGER NOT NULL,
    point_kind TEXT NOT NULL CHECK (point_kind IN ('CASH', 'BONUS')),
    balance_after INTEGER NOT NULL,
    reference_type TEXT,
    reference_id INTEGER,
    reason TEXT,
    created_by INTEGER REFERENCES users(id),
    created_at TEXT NOT NULL
);

CREATE TABLE payment_orders (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL REFERENCES users(id),
    provider TEXT NOT NULL,
    merchant_trade_no TEXT NOT NULL UNIQUE,
    amount INTEGER NOT NULL CHECK (amount >= 0),
    point_amount INTEGER NOT NULL CHECK (point_amount >= 0),
    bonus_point_amount INTEGER NOT NULL DEFAULT 0 CHECK (bonus_point_amount >= 0),
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'CANCELED', 'REFUNDED')),
    provider_payload TEXT,
    paid_at TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE kuji_campaigns (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    slug TEXT NOT NULL UNIQUE,
    title TEXT NOT NULL,
    subtitle TEXT,
    description TEXT NOT NULL,
    cover_image_url TEXT,
    banner_image_url TEXT,
    source_type TEXT NOT NULL CHECK (source_type IN ('OFFICIAL', 'SELF_MADE', 'MIXED', 'BLIND_BOX', 'CARD', 'PREORDER')),
    ip_name TEXT,
    brand_name TEXT,
    price_per_draw INTEGER NOT NULL CHECK (price_per_draw > 0),
    total_tickets INTEGER NOT NULL CHECK (total_tickets >= 0),
    remaining_tickets INTEGER NOT NULL CHECK (remaining_tickets >= 0),
    status TEXT NOT NULL CHECK (status IN ('DRAFT', 'SCHEDULED', 'LIVE', 'SOLD_OUT', 'PAUSED', 'ENDED')),
    sales_start_at TEXT,
    sales_end_at TEXT,
    shipping_note TEXT NOT NULL,
    return_policy_note TEXT NOT NULL,
    has_last_prize INTEGER NOT NULL DEFAULT 0 CHECK (has_last_prize IN (0, 1)),
    last_prize_rule TEXT,
    fairness_mode TEXT NOT NULL CHECK (fairness_mode IN ('SERVER_RANDOM', 'HASH_COMMIT_REVEAL')),
    seed_hash TEXT,
    revealed_seed TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    CHECK (remaining_tickets <= total_tickets)
);

CREATE TABLE prizes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    campaign_id INTEGER NOT NULL REFERENCES kuji_campaigns(id),
    rank TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    image_url TEXT,
    original_quantity INTEGER NOT NULL CHECK (original_quantity >= 0),
    remaining_quantity INTEGER NOT NULL CHECK (remaining_quantity >= 0),
    sort_order INTEGER NOT NULL,
    is_last_prize INTEGER NOT NULL DEFAULT 0 CHECK (is_last_prize IN (0, 1)),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    CHECK (remaining_quantity <= original_quantity)
);

CREATE TABLE kuji_tickets (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    campaign_id INTEGER NOT NULL REFERENCES kuji_campaigns(id),
    prize_id INTEGER NOT NULL REFERENCES prizes(id),
    serial_number TEXT NOT NULL UNIQUE,
    status TEXT NOT NULL CHECK (status IN ('AVAILABLE', 'DRAWN', 'VOIDED')),
    draw_id INTEGER,
    drawn_by_user_id INTEGER REFERENCES users(id),
    drawn_at TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE draw_orders (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL REFERENCES users(id),
    campaign_id INTEGER NOT NULL REFERENCES kuji_campaigns(id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    point_spent INTEGER NOT NULL CHECK (point_spent >= 0),
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'REFUNDED')),
    idempotency_key TEXT NOT NULL UNIQUE,
    client_request_id TEXT,
    ip_address TEXT,
    user_agent TEXT,
    created_at TEXT NOT NULL,
    completed_at TEXT
);

CREATE TABLE draw_results (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    draw_order_id INTEGER NOT NULL REFERENCES draw_orders(id),
    ticket_id INTEGER NOT NULL UNIQUE REFERENCES kuji_tickets(id),
    prize_id INTEGER NOT NULL REFERENCES prizes(id),
    user_id INTEGER NOT NULL REFERENCES users(id),
    campaign_id INTEGER NOT NULL REFERENCES kuji_campaigns(id),
    result_index INTEGER NOT NULL,
    random_proof TEXT,
    created_at TEXT NOT NULL
);

CREATE TABLE user_prizes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL REFERENCES users(id),
    campaign_id INTEGER NOT NULL REFERENCES kuji_campaigns(id),
    prize_id INTEGER NOT NULL REFERENCES prizes(id),
    draw_result_id INTEGER REFERENCES draw_results(id),
    status TEXT NOT NULL,
    shipment_id INTEGER,
    expires_at TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE shipments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL REFERENCES users(id),
    status TEXT NOT NULL,
    recipient_snapshot TEXT NOT NULL,
    shipping_fee INTEGER NOT NULL DEFAULT 0 CHECK (shipping_fee >= 0),
    tracking_number TEXT,
    carrier TEXT,
    admin_note TEXT,
    requested_at TEXT NOT NULL,
    shipped_at TEXT,
    delivered_at TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE coupons (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    code TEXT NOT NULL UNIQUE,
    type TEXT NOT NULL CHECK (type IN ('POINT_BONUS', 'DISCOUNT', 'FREE_SHIPPING')),
    value INTEGER NOT NULL CHECK (value >= 0),
    min_spend INTEGER NOT NULL DEFAULT 0 CHECK (min_spend >= 0),
    usage_limit INTEGER,
    used_count INTEGER NOT NULL DEFAULT 0 CHECK (used_count >= 0),
    starts_at TEXT,
    ends_at TEXT,
    status TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE audit_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    actor_id INTEGER REFERENCES users(id),
    actor_role TEXT,
    action TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id TEXT,
    before_state TEXT,
    after_state TEXT,
    ip_address TEXT,
    created_at TEXT NOT NULL
);

CREATE TABLE banners (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    image_url TEXT NOT NULL,
    href TEXT,
    position TEXT NOT NULL,
    status TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE news (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    slug TEXT NOT NULL UNIQUE,
    content TEXT NOT NULL,
    status TEXT NOT NULL,
    published_at TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE INDEX idx_campaign_status ON kuji_campaigns(status);
CREATE INDEX idx_ticket_campaign_status ON kuji_tickets(campaign_id, status);
CREATE INDEX idx_prize_campaign ON prizes(campaign_id);
CREATE INDEX idx_draw_user ON draw_orders(user_id);
CREATE INDEX idx_ledger_user ON wallet_ledger(user_id);
CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id);
