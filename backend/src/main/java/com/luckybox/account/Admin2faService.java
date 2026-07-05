package com.luckybox.account;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.luckybox.audit.AuditLogHelper;
import com.luckybox.common.ApiException;

/**
 * 管理員自助的 TOTP 二階段驗證設定。所有操作皆作用於目前登入的管理員本人，並寫入 audit log。
 */
@Service
class Admin2faService {

	private static final String ISSUER = "LuckyBox";

	private final AccountRepository accountRepository;
	private final TotpService totpService;
	private final TotpQrCodeService totpQrCodeService;
	private final AuditLogHelper auditLogHelper;

	Admin2faService(
			AccountRepository accountRepository,
			TotpService totpService,
			TotpQrCodeService totpQrCodeService,
			AuditLogHelper auditLogHelper) {
		this.accountRepository = accountRepository;
		this.totpService = totpService;
		this.totpQrCodeService = totpQrCodeService;
		this.auditLogHelper = auditLogHelper;
	}

	TwoFactorStatusResponse status() {
		SessionPrincipal admin = requireAdmin();
		return new TwoFactorStatusResponse(isEnabled(admin.user().id()));
	}

	@Transactional
	TwoFactorSetupResponse setup() {
		SessionPrincipal admin = requireAdmin();
		if (isEnabled(admin.user().id())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "TWO_FACTOR_ALREADY_ENABLED",
					"二階段驗證已啟用，請先停用再重新設定。");
			}
			String secret = totpService.generateSecret();
			accountRepository.saveTotpSecret(admin.user().id(), secret);
			String otpauthUri = totpService.otpauthUri(ISSUER, admin.user().email(), secret);
			return new TwoFactorSetupResponse(secret, otpauthUri, totpQrCodeService.dataUri(otpauthUri));
		}

	@Transactional
	TwoFactorStatusResponse enable(TwoFactorCodeRequest request) {
		SessionPrincipal admin = requireAdmin();
		TotpState state = accountRepository.findTotpState(admin.user().id()).orElse(null);
		if (state == null || state.secret() == null || state.secret().isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "TWO_FACTOR_NOT_SET_UP", "請先產生二階段驗證金鑰。");
		}
		if (state.enabled()) {
			return new TwoFactorStatusResponse(true);
		}
		if (!totpService.verify(state.secret(), request == null ? null : request.code())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "TWO_FACTOR_INVALID", "二階段驗證碼不正確。");
		}
		accountRepository.setTotpEnabled(admin.user().id(), true);
		auditLogHelper.recordActorAction(
				admin.user().id(), admin.user().role(), "ADMIN_2FA_ENABLED", "User",
				String.valueOf(admin.user().id()), "{}");
		return new TwoFactorStatusResponse(true);
	}

	@Transactional
	TwoFactorStatusResponse disable(TwoFactorCodeRequest request) {
		SessionPrincipal admin = requireAdmin();
		TotpState state = accountRepository.findTotpState(admin.user().id()).orElse(null);
		if (state == null || !state.enabled()) {
			return new TwoFactorStatusResponse(false);
		}
		if (!totpService.verify(state.secret(), request == null ? null : request.code())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "TWO_FACTOR_INVALID", "二階段驗證碼不正確。");
		}
		accountRepository.clearTotp(admin.user().id());
		auditLogHelper.recordActorAction(
				admin.user().id(), admin.user().role(), "ADMIN_2FA_DISABLED", "User",
				String.valueOf(admin.user().id()), "{}");
		return new TwoFactorStatusResponse(false);
	}

	private boolean isEnabled(long userId) {
		return accountRepository.findTotpState(userId).map(TotpState::enabled).orElse(false);
	}

	private SessionPrincipal requireAdmin() {
		return SecurityPrincipals.requireAdmin();
	}
}
