ALTER TABLE coupons ADD COLUMN vip_tier TEXT;

CREATE INDEX idx_coupons_vip_tier ON coupons(vip_tier);
