package com.luckybox.vip;

import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class VipRepository {

	private final JdbcTemplate jdbcTemplate;

	VipRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	int totalDrawSpend(long userId) {
		Integer total = jdbcTemplate.queryForObject(
				"SELECT COALESCE(SUM(point_spent), 0) FROM draw_orders WHERE user_id = ? AND status = 'COMPLETED'",
				Integer.class, userId);
		return total == null ? 0 : total;
	}

	String currentVipLevel(long userId) {
		return jdbcTemplate.query("SELECT vip_level FROM users WHERE id = ?",
				(rs, rowNum) -> rs.getString("vip_level"), userId).stream().findFirst().orElse("REGULAR");
	}

	void updateVipLevel(long userId, String vipLevel) {
		jdbcTemplate.update("UPDATE users SET vip_level = ?, updated_at = ? WHERE id = ?",
				vipLevel, Instant.now().toString(), userId);
	}
}
