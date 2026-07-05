package com.luckybox.admin.walletledger;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.luckybox.account.SecurityPrincipals;
import com.luckybox.account.SessionPrincipal;
import com.luckybox.audit.AuditLogHelper;
import com.luckybox.common.ApiException;

@Service
public class AdminWalletLedgerService {

	private static final Set<String> TYPES = Set.of(
			"TOP_UP",
			"TOP_UP_BONUS",
			"COUPON_BONUS",
			"FIRST_DEPOSIT_BONUS",
			"SPEND_THRESHOLD_BONUS",
			"CHECK_IN_BONUS",
			"DRAW_SPEND",
			"ADJUSTMENT",
			"REFUND",
			"COMPENSATION");
	private static final Set<String> POINT_KINDS = Set.of("CASH", "BONUS");

	private static final int REASON_MAX_LENGTH = 200;

	private final AdminWalletLedgerRepository adminWalletLedgerRepository;
	private final AuditLogHelper auditLogHelper;

	AdminWalletLedgerService(AdminWalletLedgerRepository adminWalletLedgerRepository, AuditLogHelper auditLogHelper) {
		this.adminWalletLedgerRepository = adminWalletLedgerRepository;
		this.auditLogHelper = auditLogHelper;
	}

	List<AdminWalletLedgerResponse> walletLedger(String type, String pointKind, String referenceType, String keyword) {
		requireAdmin();
		return adminWalletLedgerRepository.findLedger(
				normalizeType(type),
				normalizePointKind(pointKind),
				normalizeText(referenceType),
				normalizeText(keyword));
	}

	@Transactional
	public AdminWalletLedgerResponse applyAdjustment(WalletAdjustmentRequest request) {
		SessionPrincipal admin = requireAdmin();
		if (request == null || request.userId() == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WALLET_ADJUSTMENT", "請指定要調整的會員。");
		}
		String pointKind = requirePointKind(request.pointKind());
		int amount = request.amount() == null ? 0 : request.amount();
		if (amount == 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WALLET_ADJUSTMENT", "調整點數不可為 0，請輸入正數加點或負數扣點。");
		}
		String reason = request.reason() == null ? "" : request.reason().trim();
		if (reason.isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "WALLET_ADJUSTMENT_REASON_REQUIRED", "請填寫調整原因以利稽核。");
		}
		if (reason.length() > REASON_MAX_LENGTH) {
			reason = reason.substring(0, REASON_MAX_LENGTH);
		}
		if (!adminWalletLedgerRepository.userExists(request.userId())) {
			throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "找不到指定會員。");
		}

		AdminWalletLedgerResponse created = adminWalletLedgerRepository
				.applyAdjustment(request.userId(), pointKind, amount, reason, admin.user().id())
				.orElseThrow(() -> new ApiException(
						HttpStatus.BAD_REQUEST, "WALLET_ADJUSTMENT_INSUFFICIENT", "扣點後餘額不可為負，請確認調整金額。"));

		auditLogHelper.recordActorAction(
				admin.user().id(),
				admin.user().role(),
				"ADMIN_WALLET_ADJUSTED",
				"Wallet",
				String.valueOf(request.userId()),
				"{\"pointKind\":\"" + pointKind + "\",\"amount\":" + amount + ",\"ledgerId\":" + created.id() + "}");
		return created;
	}

	private SessionPrincipal requireAdmin() {
		return SecurityPrincipals.requireAdmin();
	}

	private static String requirePointKind(String pointKind) {
		if (pointKind == null || pointKind.isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_POINT_KIND", "請選擇點數種類（現金點或贈點）。");
		}
		String normalized = pointKind.trim().toUpperCase();
		if (!POINT_KINDS.contains(normalized)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_POINT_KIND", "點數種類不正確。");
		}
		return normalized;
	}

	private static String normalizeType(String type) {
		if (type == null || type.isBlank()) {
			return null;
		}
		String normalized = type.trim().toUpperCase();
		if (!TYPES.contains(normalized)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WALLET_LEDGER_TYPE", "點數流水類型不正確。");
		}
		return normalized;
	}

	private static String normalizePointKind(String pointKind) {
		if (pointKind == null || pointKind.isBlank()) {
			return null;
		}
		String normalized = pointKind.trim().toUpperCase();
		if (!POINT_KINDS.contains(normalized)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_POINT_KIND", "點數種類不正確。");
		}
		return normalized;
	}

	private static String normalizeText(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
