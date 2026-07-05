package com.luckybox.admin.auditlog;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.luckybox.account.SecurityPrincipals;
import com.luckybox.common.ApiException;

@Service
class AdminAuditLogService {

	private static final int DEFAULT_LIMIT = 100;
	private static final int MAX_LIMIT = 200;
	private static final Set<String> ACTOR_ROLES = Set.of(
			"SYSTEM",
			"SUPER_ADMIN",
			"ADMIN",
			"OPERATOR",
			"CUSTOMER_SERVICE",
			"USER");

	private final AdminAuditLogRepository adminAuditLogRepository;

	AdminAuditLogService(AdminAuditLogRepository adminAuditLogRepository) {
		this.adminAuditLogRepository = adminAuditLogRepository;
	}

	List<AdminAuditLogResponse> auditLogs(
			String action,
			String entityType,
			String actorRole,
			String keyword,
			Integer limit) {
		requireAdmin();
		return adminAuditLogRepository.findAuditLogs(
				normalizeText(action),
				normalizeText(entityType),
				normalizeActorRole(actorRole),
				normalizeText(keyword),
				normalizeLimit(limit));
	}

	private void requireAdmin() {
		SecurityPrincipals.requireAdmin();
	}

	private static String normalizeActorRole(String actorRole) {
		if (actorRole == null || actorRole.isBlank()) {
			return null;
		}
		String normalized = actorRole.trim().toUpperCase();
		if (!ACTOR_ROLES.contains(normalized)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AUDIT_ACTOR_ROLE", "操作角色不正確。");
		}
		return normalized;
	}

	private static int normalizeLimit(Integer limit) {
		if (limit == null) {
			return DEFAULT_LIMIT;
		}
		if (limit < 1 || limit > MAX_LIMIT) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AUDIT_LIMIT", "查詢筆數必須介於 1 到 200。");
		}
		return limit;
	}

	private static String normalizeText(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
