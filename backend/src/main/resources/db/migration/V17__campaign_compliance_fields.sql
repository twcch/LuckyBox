ALTER TABLE kuji_campaigns ADD COLUMN commercial_use_confirmed INTEGER NOT NULL DEFAULT 1 CHECK (commercial_use_confirmed IN (0, 1));
ALTER TABLE kuji_campaigns ADD COLUMN official_license_confirmed INTEGER NOT NULL DEFAULT 0 CHECK (official_license_confirmed IN (0, 1));
ALTER TABLE kuji_campaigns ADD COLUMN rights_notice TEXT;
ALTER TABLE kuji_campaigns ADD COLUMN age_restricted INTEGER NOT NULL DEFAULT 0 CHECK (age_restricted IN (0, 1));
ALTER TABLE kuji_campaigns ADD COLUMN minimum_age INTEGER;
ALTER TABLE kuji_campaigns ADD COLUMN age_verification_note TEXT;

UPDATE kuji_campaigns
SET
    official_license_confirmed = CASE WHEN source_type = 'OFFICIAL' THEN 1 ELSE official_license_confirmed END,
    rights_notice = CASE
        WHEN source_type = 'OFFICIAL' THEN '官方或授權商品素材已由營運確認，正式公開前仍須保留授權或進貨佐證。'
        WHEN source_type = 'SELF_MADE' THEN '自製賞素材與商品說明由營運確認可商用。'
        ELSE '商品來源與圖片素材由營運確認可於平台展示。'
    END
WHERE rights_notice IS NULL;

CREATE INDEX idx_kuji_campaigns_age_restricted ON kuji_campaigns(age_restricted);
