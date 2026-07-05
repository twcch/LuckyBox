package com.luckybox.account;

/** 2FA 設定回應：base32 金鑰、otpauth URI 與 QR data URI。尚未啟用前不應再次外洩。 */
public record TwoFactorSetupResponse(String secret, String otpauthUri, String qrCodeDataUri) {
}
