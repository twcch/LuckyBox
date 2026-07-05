package com.luckybox.account;

/** 使用者的 TOTP 狀態：base32 金鑰（可能為 null）與是否已啟用。 */
record TotpState(String secret, boolean enabled) {
}
