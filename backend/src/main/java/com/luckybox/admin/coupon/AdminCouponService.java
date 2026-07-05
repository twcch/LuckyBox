package com.luckybox.admin.coupon;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.luckybox.account.SecurityPrincipals;
import com.luckybox.account.SessionPrincipal;
import com.luckybox.audit.AuditLogHelper;
import com.luckybox.common.ApiException;
import com.luckybox.vip.VipTiers;

@Service
class AdminCouponService {

	private static final Set<String> TYPES = Set.of("POINT_BONUS", "DISCOUNT", "FREE_SHIPPING");
	private static final Set<String> STATUSES = Set.of("DRAFT", "ACTIVE", "ARCHIVED");

	private final AdminCouponRepository adminCouponRepository;
	private final AuditLogHelper auditLogHelper;

	AdminCouponService(AdminCouponRepository adminCouponRepository, AuditLogHelper auditLogHelper) {
		this.adminCouponRepository = adminCouponRepository;
		this.auditLogHelper = auditLogHelper;
	}

	List<AdminCouponResponse> coupons(String status, String type, String keyword) {
		requireAdmin();
		return adminCouponRepository.findCoupons(
				normalizeOptionalStatus(status),
				normalizeOptionalType(type),
				normalizeText(keyword));
	}

	@Transactional
	AdminCouponResponse createCoupon(AdminCouponRequest request) {
		SessionPrincipal admin = requireAdmin();
		AdminCouponRequest normalized = validateRequest(request);
		if (adminCouponRepository.findCouponByCode(normalized.code()) != null) {
			throw new ApiException(HttpStatus.CONFLICT, "COUPON_CODE_EXISTS", "優惠券代碼已存在。");
		}
		try {
			long couponId = adminCouponRepository.createCoupon(normalized);
			auditLogHelper.recordActorAction(
					admin.user().id(),
					admin.user().role(),
					"ADMIN_COUPON_CREATED",
					"Coupon",
					String.valueOf(couponId),
					"{\"code\":\"" + normalized.code() + "\",\"status\":\"" + normalized.status() + "\"}");
			return adminCouponRepository.findCoupon(couponId);
		} catch (DataAccessException exception) {
			throw couponWriteException(exception);
		}
	}

	@Transactional
	AdminCouponResponse updateCoupon(long couponId, AdminCouponRequest request) {
		SessionPrincipal admin = requireAdmin();
		AdminCouponResponse current = adminCouponRepository.findCoupon(couponId);
		if (current == null) {
			throw new ApiException(HttpStatus.NOT_FOUND, "COUPON_NOT_FOUND", "找不到指定優惠券。");
		}
		AdminCouponRequest normalized = validateRequest(request);
		AdminCouponResponse codeOwner = adminCouponRepository.findCouponByCode(normalized.code());
		if (codeOwner != null && codeOwner.id() != couponId) {
			throw new ApiException(HttpStatus.CONFLICT, "COUPON_CODE_EXISTS", "優惠券代碼已存在。");
		}
		if (normalized.usageLimit() != null && normalized.usageLimit() < current.usedCount()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "COUPON_USAGE_LIMIT_TOO_LOW", "優惠券使用上限不可低於已使用次數。");
		}
		try {
			if (!adminCouponRepository.updateCoupon(couponId, normalized)) {
				throw new ApiException(HttpStatus.NOT_FOUND, "COUPON_NOT_FOUND", "找不到指定優惠券。");
			}
		} catch (DataAccessException exception) {
			throw couponWriteException(exception);
		}
		auditLogHelper.recordActorAction(
				admin.user().id(),
				admin.user().role(),
				"ADMIN_COUPON_UPDATED",
				"Coupon",
				String.valueOf(couponId),
				"{\"code\":\"" + normalized.code() + "\",\"from\":\"" + current.status() + "\",\"to\":\"" + normalized.status() + "\"}");
		return adminCouponRepository.findCoupon(couponId);
	}

	private AdminCouponRequest validateRequest(AdminCouponRequest request) {
		String code = normalizeCode(request.code());
		String type = normalizeType(request.type());
		String vipTier = normalizeOptionalVipTier(request.vipTier());
		String status = normalizeStatus(request.status());
		int value = normalizeValue(type, request.value());
		int minSpend = normalizeMinSpend(request.minSpend());
		Integer usageLimit = normalizeUsageLimit(request.usageLimit());
		String startsAt = normalizeOptionalInstant(request.startsAt(), "INVALID_COUPON_STARTS_AT", "優惠券開始時間必須是合法 ISO 時間。");
		String endsAt = normalizeOptionalInstant(request.endsAt(), "INVALID_COUPON_ENDS_AT", "優惠券結束時間必須是合法 ISO 時間。");
		if (startsAt != null && endsAt != null && Instant.parse(startsAt).isAfter(Instant.parse(endsAt))) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COUPON_PERIOD", "優惠券開始時間不可晚於結束時間。");
		}
		return new AdminCouponRequest(code, type, vipTier, value, minSpend, usageLimit, startsAt, endsAt, status);
	}

	private SessionPrincipal requireAdmin() {
		return SecurityPrincipals.requireAdmin();
	}

	private static String normalizeCode(String code) {
		String normalized = requireText(code, "INVALID_COUPON_CODE", "優惠券代碼不可空白。").toUpperCase();
		if (!normalized.matches("[A-Z0-9][A-Z0-9_-]{2,31}")) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COUPON_CODE", "優惠券代碼只能使用英文、數字、底線與連字號，長度 3-32。");
		}
		return normalized;
	}

	private static String normalizeOptionalType(String type) {
		if (type == null || type.isBlank()) {
			return null;
		}
		return normalizeType(type);
	}

	private static String normalizeType(String type) {
		String normalized = requireText(type, "INVALID_COUPON_TYPE", "優惠券類型不正確。").toUpperCase();
		if (!TYPES.contains(normalized)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COUPON_TYPE", "優惠券類型不正確。");
		}
		return normalized;
	}

	private static String normalizeOptionalVipTier(String vipTier) {
		if (vipTier == null || vipTier.isBlank()) {
			return null;
		}
		String normalized = VipTiers.normalize(vipTier);
		if (!VipTiers.isCouponRequiredTier(normalized)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COUPON_VIP_TIER", "VIP 適用等級不正確。");
		}
		return normalized;
	}

	private static String normalizeOptionalStatus(String status) {
		if (status == null || status.isBlank()) {
			return null;
		}
		return normalizeStatus(status);
	}

	private static String normalizeStatus(String status) {
		String normalized = requireText(status, "INVALID_COUPON_STATUS", "優惠券狀態不正確。").toUpperCase();
		if (!STATUSES.contains(normalized)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COUPON_STATUS", "優惠券狀態不正確。");
		}
		return normalized;
	}

	private static int normalizeValue(String type, int value) {
		if (value < 0 || (!"FREE_SHIPPING".equals(type) && value == 0)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COUPON_VALUE", "優惠券面額不正確。");
		}
		return value;
	}

	private static int normalizeMinSpend(int minSpend) {
		if (minSpend < 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COUPON_MIN_SPEND", "最低消費不可小於 0。");
		}
		return minSpend;
	}

	private static Integer normalizeUsageLimit(Integer usageLimit) {
		if (usageLimit != null && usageLimit <= 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COUPON_USAGE_LIMIT", "使用上限必須大於 0。");
		}
		return usageLimit;
	}

	private static String normalizeOptionalInstant(String value, String code, String message) {
		String normalized = AdminCouponRepository.cleanOptional(value);
		if (normalized == null) {
			return null;
		}
		try {
			return Instant.parse(normalized).toString();
		} catch (RuntimeException exception) {
			throw new ApiException(HttpStatus.BAD_REQUEST, code, message);
		}
	}

	private static String requireText(String value, String code, String message) {
		if (value == null || value.isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, code, message);
		}
		return value.trim();
	}

	private static String normalizeText(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static ApiException couponWriteException(DataAccessException exception) {
		if (exception.getMessage() != null && exception.getMessage().contains("coupons.code")) {
			return new ApiException(HttpStatus.CONFLICT, "COUPON_CODE_EXISTS", "優惠券代碼已存在。");
		}
		return new ApiException(HttpStatus.BAD_REQUEST, "COUPON_WRITE_FAILED", "優惠券資料不符合資料庫限制。");
	}
}
