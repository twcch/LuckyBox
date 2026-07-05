PRAGMA foreign_keys=OFF;

CREATE TABLE kuji_campaigns_new (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    slug TEXT NOT NULL UNIQUE,
    title TEXT NOT NULL,
    subtitle TEXT,
    description TEXT NOT NULL,
    cover_image_url TEXT,
    banner_image_url TEXT,
    source_type TEXT NOT NULL CHECK (source_type IN ('OFFICIAL', 'SELF_MADE', 'MIXED', 'BLIND_BOX', 'CARD', 'GK', 'PREORDER')),
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
    server_seed TEXT,
    commercial_use_confirmed INTEGER NOT NULL DEFAULT 1 CHECK (commercial_use_confirmed IN (0, 1)),
    official_license_confirmed INTEGER NOT NULL DEFAULT 0 CHECK (official_license_confirmed IN (0, 1)),
    rights_notice TEXT,
    age_restricted INTEGER NOT NULL DEFAULT 0 CHECK (age_restricted IN (0, 1)),
    minimum_age INTEGER,
    age_verification_note TEXT,
    CHECK (remaining_tickets <= total_tickets)
);

INSERT INTO kuji_campaigns_new (
    id, slug, title, subtitle, description, cover_image_url, banner_image_url, source_type,
    ip_name, brand_name, price_per_draw, total_tickets, remaining_tickets, status,
    sales_start_at, sales_end_at, shipping_note, return_policy_note, has_last_prize,
    last_prize_rule, fairness_mode, seed_hash, revealed_seed, created_at, updated_at,
    server_seed, commercial_use_confirmed, official_license_confirmed, rights_notice,
    age_restricted, minimum_age, age_verification_note
)
SELECT
    id, slug, title, subtitle, description, cover_image_url, banner_image_url, source_type,
    ip_name, brand_name, price_per_draw, total_tickets, remaining_tickets, status,
    sales_start_at, sales_end_at, shipping_note, return_policy_note, has_last_prize,
    last_prize_rule, fairness_mode, seed_hash, revealed_seed, created_at, updated_at,
    server_seed, commercial_use_confirmed, official_license_confirmed, rights_notice,
    age_restricted, minimum_age, age_verification_note
FROM kuji_campaigns;

DROP TABLE kuji_campaigns;
ALTER TABLE kuji_campaigns_new RENAME TO kuji_campaigns;

CREATE INDEX idx_campaign_status ON kuji_campaigns(status);
CREATE INDEX idx_kuji_campaigns_age_restricted ON kuji_campaigns(age_restricted);

PRAGMA foreign_keys=ON;
