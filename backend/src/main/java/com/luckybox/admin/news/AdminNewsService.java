package com.luckybox.admin.news;

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

@Service
class AdminNewsService {

	private static final Set<String> STATUSES = Set.of("DRAFT", "PUBLISHED", "ARCHIVED");

	private final AdminNewsRepository adminNewsRepository;
	private final AuditLogHelper auditLogHelper;

	AdminNewsService(AdminNewsRepository adminNewsRepository, AuditLogHelper auditLogHelper) {
		this.adminNewsRepository = adminNewsRepository;
		this.auditLogHelper = auditLogHelper;
	}

	List<AdminNewsResponse> news(String status, String keyword) {
		requireAdmin();
		return adminNewsRepository.findNews(normalizeOptionalStatus(status), normalizeText(keyword));
	}

	@Transactional
	AdminNewsResponse createNews(AdminNewsRequest request) {
		SessionPrincipal admin = requireAdmin();
		AdminNewsRequest normalized = validateRequest(request);
		if (adminNewsRepository.findNewsBySlug(normalized.slug()) != null) {
			throw new ApiException(HttpStatus.CONFLICT, "NEWS_SLUG_EXISTS", "公告代碼已存在，請使用不同 slug。");
		}
		try {
			long newsId = adminNewsRepository.createNews(normalized);
			auditLogHelper.recordActorAction(
					admin.user().id(),
					admin.user().role(),
					"ADMIN_NEWS_CREATED",
					"News",
					String.valueOf(newsId),
					"{\"slug\":\"" + normalized.slug() + "\",\"status\":\"" + normalized.status() + "\"}");
			return adminNewsRepository.findNews(newsId);
		} catch (DataAccessException exception) {
			throw newsWriteException(exception);
		}
	}

	@Transactional
	AdminNewsResponse updateNews(long newsId, AdminNewsRequest request) {
		SessionPrincipal admin = requireAdmin();
		AdminNewsResponse current = adminNewsRepository.findNews(newsId);
		if (current == null) {
			throw new ApiException(HttpStatus.NOT_FOUND, "NEWS_NOT_FOUND", "找不到指定公告。");
		}
		AdminNewsRequest normalized = validateRequest(request);
		AdminNewsResponse slugOwner = adminNewsRepository.findNewsBySlug(normalized.slug());
		if (slugOwner != null && slugOwner.id() != newsId) {
			throw new ApiException(HttpStatus.CONFLICT, "NEWS_SLUG_EXISTS", "公告代碼已存在，請使用不同 slug。");
		}
		try {
			if (!adminNewsRepository.updateNews(newsId, normalized)) {
				throw new ApiException(HttpStatus.NOT_FOUND, "NEWS_NOT_FOUND", "找不到指定公告。");
			}
		} catch (DataAccessException exception) {
			throw newsWriteException(exception);
		}
		auditLogHelper.recordActorAction(
				admin.user().id(),
				admin.user().role(),
				"ADMIN_NEWS_UPDATED",
				"News",
				String.valueOf(newsId),
				"{\"slug\":\"" + normalized.slug() + "\",\"from\":\"" + current.status() + "\",\"to\":\"" + normalized.status() + "\"}");
		return adminNewsRepository.findNews(newsId);
	}

	private AdminNewsRequest validateRequest(AdminNewsRequest request) {
		String title = requireText(request.title(), "INVALID_NEWS_TITLE", "公告標題不可空白。");
		String slug = normalizeSlug(request.slug());
		String content = requireText(request.content(), "INVALID_NEWS_CONTENT", "公告內容不可空白。");
		String status = normalizeStatus(request.status());
		String publishedAt = normalizePublishedAt(request.publishedAt());
		if ("PUBLISHED".equals(status) && publishedAt == null) {
			publishedAt = Instant.now().toString();
		}
		if (!"PUBLISHED".equals(status)) {
			publishedAt = null;
		}
		String unpublishAt = normalizeUnpublishAt(request.unpublishAt());
		if (publishedAt != null && unpublishAt != null
				&& !Instant.parse(unpublishAt).isAfter(Instant.parse(publishedAt))) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NEWS_SCHEDULE", "下架時間必須晚於發布時間。");
		}
		return new AdminNewsRequest(title, slug, content, status, publishedAt, unpublishAt);
	}

	private static String normalizeUnpublishAt(String unpublishAt) {
		String normalized = AdminNewsRepository.cleanOptional(unpublishAt);
		if (normalized == null) {
			return null;
		}
		try {
			return Instant.parse(normalized).toString();
		}
		catch (RuntimeException exception) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NEWS_UNPUBLISH_AT", "下架時間必須是合法 ISO 時間。");
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
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NEWS_STATUS", "公告狀態不正確。");
		}
		String normalized = status.trim().toUpperCase();
		if (!STATUSES.contains(normalized)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NEWS_STATUS", "公告狀態不正確。");
		}
		return normalized;
	}

	private static String normalizeSlug(String slug) {
		String normalized = requireText(slug, "INVALID_NEWS_SLUG", "公告代碼不可空白。").toLowerCase();
		if (!normalized.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NEWS_SLUG", "公告代碼只能使用小寫英文、數字與連字號。");
		}
		return normalized;
	}

	private static String normalizePublishedAt(String publishedAt) {
		String normalized = AdminNewsRepository.cleanOptional(publishedAt);
		if (normalized == null) {
			return null;
		}
		try {
			return Instant.parse(normalized).toString();
		} catch (RuntimeException exception) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NEWS_PUBLISHED_AT", "公告發布時間必須是合法 ISO 時間。");
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

	private static ApiException newsWriteException(DataAccessException exception) {
		if (exception.getMessage() != null && exception.getMessage().contains("news.slug")) {
			return new ApiException(HttpStatus.CONFLICT, "NEWS_SLUG_EXISTS", "公告代碼已存在，請使用不同 slug。");
		}
		return new ApiException(HttpStatus.BAD_REQUEST, "NEWS_WRITE_FAILED", "公告資料不符合資料庫限制。");
	}
}
