ALTER TABLE draw_orders ADD COLUMN original_point_spent INTEGER;
ALTER TABLE draw_orders ADD COLUMN discount_amount INTEGER NOT NULL DEFAULT 0;
ALTER TABLE draw_orders ADD COLUMN coupon_id INTEGER REFERENCES coupons(id);

CREATE TABLE coupon_usages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    coupon_id INTEGER NOT NULL REFERENCES coupons(id),
    user_id INTEGER NOT NULL REFERENCES users(id),
    reference_type TEXT NOT NULL,
    reference_id INTEGER NOT NULL,
    discount_amount INTEGER NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
    point_amount INTEGER NOT NULL DEFAULT 0 CHECK (point_amount >= 0),
    status TEXT NOT NULL CHECK (status IN ('APPLIED', 'CANCELED', 'REFUNDED')),
    used_at TEXT NOT NULL
);

CREATE UNIQUE INDEX ux_coupon_usages_coupon_user ON coupon_usages(coupon_id, user_id);
CREATE INDEX idx_coupon_usages_user ON coupon_usages(user_id);
CREATE INDEX idx_coupon_usages_reference ON coupon_usages(reference_type, reference_id);
