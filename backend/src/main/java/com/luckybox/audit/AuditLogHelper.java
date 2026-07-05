package com.luckybox.audit;

import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AuditLogHelper {

	private final JdbcTemplate jdbcTemplate;

	public AuditLogHelper(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public void recordSystemAction(String action, String entityType, String entityId, String afterState) {
		jdbcTemplate.update("""
				INSERT INTO audit_logs (
					actor_id, actor_role, action, entity_type, entity_id, before_state, after_state, ip_address, created_at
				)
				VALUES (NULL, 'SYSTEM', ?, ?, ?, NULL, ?, NULL, ?)
				""", action, entityType, entityId, afterState, Instant.now().toString());
	}

	public void recordActorAction(long actorId, String actorRole, String action, String entityType, String entityId, String afterState) {
		jdbcTemplate.update("""
				INSERT INTO audit_logs (
					actor_id, actor_role, action, entity_type, entity_id, before_state, after_state, ip_address, created_at
				)
				VALUES (?, ?, ?, ?, ?, NULL, ?, NULL, ?)
				""", actorId, actorRole, action, entityType, entityId, afterState, Instant.now().toString());
	}

}
