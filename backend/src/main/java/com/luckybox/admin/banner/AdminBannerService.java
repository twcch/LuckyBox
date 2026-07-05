package com.luckybox.admin.banner;

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
class AdminBannerService {

	private static final Set<String> POSITIONS = Set.of("HOME_HERO", "HOME_SECTION");
	private static final Set<String> STATUSES = Set.of("DRAFT", "ACTIVE", "ARCHIVED");

	private final AdminBannerRepository adminBannerRepository;
	private final AuditLogHelper auditLogHelper;

	AdminBannerService(AdminBannerRepository adminBannerRepository, AuditLogHelper auditLogHelper) {
		this.adminBannerRepository = adminBannerRepository;
		this.auditLogHelper = auditLogHelper;
	}

	List<AdminBannerResponse> banners(String status, String position, String keyword) {
		requireAdmin();
		return adminBannerRepository.findBanners(
				normalizeOptionalStatus(status),
				normalizeOptionalPosition(position),
				normalizeText(keyword));
	}

	@Transactional
	AdminBannerResponse createBanner(AdminBannerRequest request) {
		SessionPrincipal admin = requireAdmin();
		AdminBannerRequest normalized = validateRequest(request);
		long bannerId = adminBannerRepository.createBanner(normalized);
		auditLogHelper.recordActorAction(
				admin.user().id(),
				admin.user().role(),
				"ADMIN_BANNER_CREATED",
				"Banner",
				String.valueOf(bannerId),
				"{\"position\":\"" + normalized.position() + "\",\"status\":\"" + normalized.status() + "\"}");
		return adminBannerRepository.findBanner(bannerId);
	}

	@Transactional
	AdminBannerResponse updateBanner(long bannerId, AdminBannerRequest request) {
		SessionPrincipal admin = requireAdmin();
		AdminBannerResponse current = adminBannerRepository.findBanner(bannerId);
		if (current == null) {
			throw new ApiException(HttpStatus.NOT_FOUND, "BANNER_NOT_FOUND", "找不到指定 Banner。");
		}
		AdminBannerRequest normalized = validateRequest(request);
		if (!adminBannerRepository.updateBanner(bannerId, normalized)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "BANNER_NOT_FOUND", "找不到指定 Banner。");
		}
		auditLogHelper.recordActorAction(
				admin.user().id(),
				admin.user().role(),
				"ADMIN_BANNER_UPDATED",
				"Banner",
				String.valueOf(bannerId),
				"{\"position\":\"" + normalized.position() + "\",\"from\":\"" + current.status() + "\",\"to\":\"" + normalized.status() + "\"}");
		return adminBannerRepository.findBanner(bannerId);
	}

	private AdminBannerRequest validateRequest(AdminBannerRequest request) {
		String title = requireText(request.title(), "INVALID_BANNER_TITLE", "Banner 標題不可空白。");
		String imageUrl = normalizeImageUrl(request.imageUrl());
		String href = normalizeHref(request.href());
		String position = normalizePosition(request.position());
		String status = normalizeStatus(request.status());
		String publishAt = normalizeIsoTime(request.publishAt(), "INVALID_BANNER_PUBLISH_AT", "上架時間必須是合法 ISO 時間。");
		String unpublishAt = normalizeIsoTime(request.unpublishAt(), "INVALID_BANNER_UNPUBLISH_AT", "下架時間必須是合法 ISO 時間。");
		if (publishAt != null && unpublishAt != null
				&& !java.time.Instant.parse(unpublishAt).isAfter(java.time.Instant.parse(publishAt))) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BANNER_SCHEDULE", "下架時間必須晚於上架時間。");
		}
		return new AdminBannerRequest(title, imageUrl, href, position, status, publishAt, unpublishAt);
	}

	private static String normalizeIsoTime(String value, String code, String message) {
		String normalized = AdminBannerRepository.cleanOptional(value);
		if (normalized == null) {
			return null;
		}
		try {
			return java.time.Instant.parse(normalized).toString();
		}
		catch (RuntimeException exception) {
			throw new ApiException(HttpStatus.BAD_REQUEST, code, message);
		}
	}

	private SessionPrincipal requireAdmin() {
		return SecurityPrincipals.requireAdmin();
	}

	private static String normalizeOptionalStatus(String status) {
		if (status == null || status.isBlank()) {
			return null;
		}
		return normalizeStatus(status);
	}

	private static String normalizeStatus(String status) {
		if (status == null || status.isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BANNER_STATUS", "Banner 狀態不正確。");
		}
		String normalized = status.trim().toUpperCase();
		if (!STATUSES.contains(normalized)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BANNER_STATUS", "Banner 狀態不正確。");
		}
		return normalized;
	}

	private static String normalizeOptionalPosition(String position) {
		if (position == null || position.isBlank()) {
			return null;
		}
		return normalizePosition(position);
	}

	private static String normalizePosition(String position) {
		if (position == null || position.isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BANNER_POSITION", "Banner 位置不正確。");
		}
		String normalized = position.trim().toUpperCase();
		if (!POSITIONS.contains(normalized)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BANNER_POSITION", "Banner 位置不正確。");
		}
		return normalized;
	}

	private static String normalizeImageUrl(String imageUrl) {
		String normalized = requireText(imageUrl, "INVALID_BANNER_IMAGE_URL", "Banner 圖片網址不可空白。");
		if (!isPublicLink(normalized)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BANNER_IMAGE_URL", "Banner 圖片網址必須是 http(s) 或站內路徑。");
		}
		return normalized;
	}

	private static String normalizeHref(String href) {
		String normalized = AdminBannerRepository.cleanOptional(href);
		if (normalized == null) {
			return null;
		}
		if (!isPublicLink(normalized) && !normalized.startsWith("#")) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BANNER_HREF", "Banner 連結必須是 http(s)、站內路徑或頁面錨點。");
		}
		return normalized;
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

	private static boolean isPublicLink(String value) {
		return value.startsWith("https://") || value.startsWith("http://") || value.startsWith("/");
	}
}
