package com.luckybox.wish;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.luckybox.account.SecurityPrincipals;
import com.luckybox.account.SessionPrincipal;
import com.luckybox.audit.AuditLogHelper;
import com.luckybox.common.ApiException;

@Service
class WishService {

	private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");
	private static final int CONTENT_MIN_LENGTH = 4;
	private static final int CONTENT_MAX_LENGTH = 200;
	private static final int DAILY_WISH_LIMIT = 5;
	private static final int PUBLIC_LIMIT = 50;
	private static final int MY_LIMIT = 50;
	private static final int ADMIN_LIMIT = 200;
	private static final Set<String> MODERATION_STATUSES = Set.of("APPROVED", "REJECTED", "HIDDEN", "PENDING");

	private final WishRepository wishRepository;
	private final AuditLogHelper auditLogHelper;
	private final boolean autoApprove;

	WishService(
			WishRepository wishRepository,
			AuditLogHelper auditLogHelper,
			@Value("${luckybox.wish.auto-approve:true}") boolean autoApprove) {
		this.wishRepository = wishRepository;
		this.auditLogHelper = auditLogHelper;
		this.autoApprove = autoApprove;
	}

	@Transactional(readOnly = true)
	List<WishResponse> publicWishes() {
		return wishRepository.publicWishes(PUBLIC_LIMIT);
	}

	@Transactional(readOnly = true)
	List<WishResponse> myWishes() {
		return wishRepository.wishesByUser(currentUserId(), MY_LIMIT);
	}

	@Transactional
	WishResponse createWish(CreateWishRequest request) {
		long userId = currentUserId();
		String content = normalizeContent(request == null ? null : request.content());

		String today = LocalDate.now(TAIPEI).toString();
		if (wishRepository.countByUserToday(userId, today) >= DAILY_WISH_LIMIT) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "WISH_DAILY_LIMIT", "今日投稿次數已達上限，請明天再試。");
		}

		String status = autoApprove ? "APPROVED" : "PENDING";
		long wishId = wishRepository.createWish(userId, content, status);
		auditLogHelper.recordSystemAction(
				"WISH_CREATED",
				"Wish",
				String.valueOf(wishId),
				"{\"userId\":" + userId + ",\"status\":\"" + status + "\"}");
		return wishRepository.wishesByUser(userId, MY_LIMIT).stream()
				.filter(wish -> wish.id() == wishId)
				.findFirst()
				.orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "WISH_CREATE_FAILED", "願望建立失敗。"));
	}

	@Transactional(readOnly = true)
	List<AdminWishResponse> adminWishes(String status) {
		requireAdmin();
		return wishRepository.adminWishes(normalizeOptionalStatus(status), ADMIN_LIMIT);
	}

	@Transactional
	AdminWishResponse moderateWish(long wishId, ModerateWishRequest request) {
		SessionPrincipal admin = requireAdmin();
		AdminWishResponse current = wishRepository.findAdminWish(wishId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WISH_NOT_FOUND", "找不到指定願望。"));
		String status = normalizeModerationStatus(request == null ? null : request.status());
		String note = normalizeNote(request == null ? null : request.note());

		wishRepository.moderateWish(wishId, status, note, admin.user().id());
		auditLogHelper.recordActorAction(
				admin.user().id(),
				admin.user().role(),
				"ADMIN_WISH_MODERATED",
				"Wish",
				String.valueOf(wishId),
				"{\"from\":\"" + current.status() + "\",\"to\":\"" + status + "\"}");
		return wishRepository.findAdminWish(wishId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WISH_NOT_FOUND", "找不到指定願望。"));
	}

	private static String normalizeContent(String content) {
		if (content == null || content.isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WISH_CONTENT", "願望內容不可空白。");
		}
		String normalized = content.strip();
		if (normalized.codePointCount(0, normalized.length()) < CONTENT_MIN_LENGTH) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WISH_CONTENT", "願望內容至少需 4 個字。");
		}
		if (normalized.codePointCount(0, normalized.length()) > CONTENT_MAX_LENGTH) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WISH_CONTENT", "願望內容請控制在 200 字以內。");
		}
		return normalized;
	}

	private static String normalizeOptionalStatus(String status) {
		if (status == null || status.isBlank()) {
			return null;
		}
		String normalized = status.strip().toUpperCase();
		if (!MODERATION_STATUSES.contains(normalized)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WISH_STATUS", "願望狀態不正確。");
		}
		return normalized;
	}

	private static String normalizeModerationStatus(String status) {
		if (status == null || status.isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WISH_STATUS", "願望狀態不正確。");
		}
		String normalized = status.strip().toUpperCase();
		if (!MODERATION_STATUSES.contains(normalized)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WISH_STATUS", "願望狀態不正確。");
		}
		return normalized;
	}

	private static String normalizeNote(String note) {
		if (note == null || note.isBlank()) {
			return null;
		}
		String normalized = note.strip();
		return normalized.length() > CONTENT_MAX_LENGTH ? normalized.substring(0, CONTENT_MAX_LENGTH) : normalized;
	}

	private long currentUserId() {
		return SecurityPrincipals.requireAuthenticated().user().id();
	}

	private SessionPrincipal requireAdmin() {
		return SecurityPrincipals.requireAdmin();
	}
}
