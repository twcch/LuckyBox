-- 後台管理員二階段驗證（TOTP / RFC 6238）：每位使用者可選擇性啟用，預設關閉。
ALTER TABLE users ADD COLUMN totp_secret TEXT;
ALTER TABLE users ADD COLUMN totp_enabled INTEGER NOT NULL DEFAULT 0;
