package com.luckybox.admin.user;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.luckybox.account.SecurityPrincipals;
import com.luckybox.account.SessionPrincipal;
import com.luckybox.audit.AuditLogHelper;
import com.luckybox.common.ApiException;
import com.luckybox.notification.NotificationService;

@Service
public class AdminUserService {

	private static final Set<String> STATUSES = Set.of("ACTIVE", "SUSPENDED", "DELETED");
	private static final Set<String> ROLES = Set.of("USER", "CUSTOMER_SERVICE", "OPERATOR", "ADMIN", "SUPER_ADMIN");
	private static final Set<String> ASSIGNABLE_ROLES = Set.of("USER", "CUSTOMER_SERVICE", "OPERATOR", "ADMIN");
	private static final Set<String> MUTABLE_STATUSES = Set.of("ACTIVE", "SUSPENDED");
	private static final int NOTE_MAX_LENGTH = 500;
	private static final int COMPENSATION_MAX = 1_000_000;

	private final AdminUserRepository adminUserRepository;
	private final AuditLogHelper auditLogHelper;
	private final NotificationService notificationService;

	AdminUserService(
			AdminUserRepository adminUserRepository,
			AuditLogHelper auditLogHelper,
			NotificationService notificationService) {
		this.adminUserRepository = adminUserRepository;
		this.auditLogHelper = auditLogHelper;
		this.notificationService = notificationService;
	}

	List<AdminUserResponse> users(String status, String role, String keyword) {
		requireAdmin();
		return adminUserRepository.findUsers(
				normalizeOptional(status, STATUSES, "INVALID_USER_STATUS", "會員狀態不正確。"),
				normalizeOptional(role, ROLES, "INVALID_USER_ROLE", "會員角色不正確。"),
				normalizeKeyword(keyword));
	}

	@Transactional
	AdminMemberDetailResponse memberDetail(long userId, boolean revealPii) {
		SessionPrincipal admin = requireAdmin();
		AdminMemberDetailResponse detail = adminUserRepository.findMemberDetail(userId, revealPii);
		if (detail == null) {
			throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "找不到指定會員。");
		}
		if (revealPii) {
			// 查閱完整個資屬敏感存取，依個資治理留下稽核紀錄（誰、何時、查了哪位會員）。
			auditLogHelper.recordActorAction(
					admin.user().id(),
					admin.user().role(),
					"ADMIN_MEMBER_DETAIL_VIEWED",
					"User",
					String.valueOf(userId),
					"{\"reveal\":true}");
		}
		return detail;
	}

	@Transactional
	AdminMemberDetailResponse.Note addMemberNote(long userId, MemberNoteRequest request) {
		SessionPrincipal admin = requireAdmin();
		String content = request == null ? "" : (request.content() == null ? "" : request.content().strip());
		if (content.isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MEMBER_NOTE", "備註內容不可空白。");
		}
		if (content.length() > NOTE_MAX_LENGTH) {
			content = content.substring(0, NOTE_MAX_LENGTH);
		}
		if (!adminUserRepository.userExists(userId)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "找不到指定會員。");
		}
		long noteId = adminUserRepository.addMemberNote(userId, admin.user().id(), content);
		auditLogHelper.recordActorAction(
				admin.user().id(),
				admin.user().role(),
				"ADMIN_MEMBER_NOTE_ADDED",
				"User",
				String.valueOf(userId),
				"{\"noteId\":" + noteId + "}");
		return adminUserRepository.loadNotes(userId).stream()
				.filter(note -> note.id() == noteId)
				.findFirst()
				.orElseThrow(() -> new ApiException(
						HttpStatus.INTERNAL_SERVER_ERROR, "MEMBER_NOTE_CREATE_FAILED", "備註建立失敗。"));
	}

	@Transactional
	public CompensationResponse grantCompensation(long userId, CompensationRequest request) {
		SessionPrincipal admin = requireAdmin();
		int amount = request == null || request.amount() == null ? 0 : request.amount();
		if (amount <= 0 || amount > COMPENSATION_MAX) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COMPENSATION_AMOUNT", "補償點數須為正整數且在合理範圍內。");
		}
		String reason = request == null || request.reason() == null ? "" : request.reason().strip();
		if (reason.isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "COMPENSATION_REASON_REQUIRED", "請填寫補償原因以利稽核。");
		}
		if (reason.length() > NOTE_MAX_LENGTH) {
			reason = reason.substring(0, NOTE_MAX_LENGTH);
		}
		if (!adminUserRepository.userExists(userId)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "找不到指定會員。");
		}
		CompensationResponse result = adminUserRepository.grantCompensation(userId, amount, reason, admin.user().id());
		auditLogHelper.recordActorAction(
				admin.user().id(),
				admin.user().role(),
				"ADMIN_COMPENSATION_GRANTED",
				"User",
				String.valueOf(userId),
				"{\"amount\":" + amount + ",\"ledgerId\":" + result.ledgerId() + "}");
		notificationService.notifyCompensation(userId, amount);
		return result;
	}

	@Transactional
	AdminUserResponse updateStatus(long userId, AdminUserStatusRequest request) {
		SessionPrincipal admin = requireAdmin();
		String status = normalizeRequired(request.status(), MUTABLE_STATUSES, "INVALID_USER_STATUS", "會員狀態只能切換為啟用或停權。");
		AdminUserRepository.UserRow target = adminUserRepository.findUser(userId);
		if (target == null) {
			throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "找不到指定會員。");
		}
		if (admin.user().id() == userId) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_UPDATE_SELF", "不可變更自己的帳號狀態。");
		}
		if ("SUPER_ADMIN".equals(target.role())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "SUPER_ADMIN_PROTECTED", "不可變更超級管理員狀態。");
		}
		if (status.equals(target.status())) {
			return adminUserRepository.findResponse(userId);
		}
		if (!adminUserRepository.updateStatus(userId, status)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "找不到指定會員。");
		}
		auditLogHelper.recordActorAction(
				admin.user().id(),
				admin.user().role(),
				"ADMIN_USER_STATUS_UPDATED",
				"User",
				String.valueOf(userId),
				"{\"from\":\"" + target.status() + "\",\"to\":\"" + status + "\"}");
		return adminUserRepository.findResponse(userId);
	}

	@Transactional
	AdminUserResponse updateRole(long userId, AdminUserRoleRequest request) {
		SessionPrincipal admin = requireSuperAdmin();
		String role = normalizeRequired(request.role(), ASSIGNABLE_ROLES, "INVALID_USER_ROLE",
				"會員角色只能調整為會員、客服、營運或管理員。");
		AdminUserRepository.UserRow target = adminUserRepository.findUser(userId);
		if (target == null) {
			throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "找不到指定會員。");
		}
		if (admin.user().id() == userId) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_UPDATE_SELF", "不可變更自己的帳號角色。");
		}
		if ("SUPER_ADMIN".equals(target.role())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "SUPER_ADMIN_PROTECTED", "不可變更超級管理員角色。");
		}
		if (role.equals(target.role())) {
			return adminUserRepository.findResponse(userId);
		}
		if (!adminUserRepository.updateRole(userId, role)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "找不到指定會員。");
		}
		auditLogHelper.recordActorAction(
				admin.user().id(),
				admin.user().role(),
				"ADMIN_USER_ROLE_UPDATED",
				"User",
				String.valueOf(userId),
				"{\"from\":\"" + target.role() + "\",\"to\":\"" + role + "\"}");
		return adminUserRepository.findResponse(userId);
	}

	private SessionPrincipal requireAdmin() {
		return SecurityPrincipals.requireAdmin();
	}

	private SessionPrincipal requireSuperAdmin() {
		return SecurityPrincipals.requireSuperAdmin();
	}

	private static String normalizeOptional(String value, Set<String> allowedValues, String code, String message) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return normalizeRequired(value, allowedValues, code, message);
	}

	private static String normalizeRequired(String value, Set<String> allowedValues, String code, String message) {
		if (value == null || value.isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, code, message);
		}
		String normalized = value.trim().toUpperCase();
		if (!allowedValues.contains(normalized)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, code, message);
		}
		return normalized;
	}

	private static String normalizeKeyword(String keyword) {
		return keyword == null || keyword.isBlank() ? null : keyword.trim();
	}
}
