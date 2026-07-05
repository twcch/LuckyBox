package com.luckybox.admin.auditlog;

public record AdminAuditLogResponse(
		long id,
		Long actorId,
		String actorDisplayName,
		String maskedActorEmail,
		String actorRole,
		String actorRoleLabel,
		String action,
		String actionLabel,
		String entityType,
		String entityTypeLabel,
		String entityId,
		String beforeState,
		String afterState,
		String ipAddress,
		String createdAt) {
}
