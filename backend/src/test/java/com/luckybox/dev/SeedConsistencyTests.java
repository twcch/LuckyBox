package com.luckybox.dev;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class SeedConsistencyTests {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void seedCreatesAdminAndThreeCampaigns() {
		Integer adminCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM users WHERE email = 'admin@luckybox.local' AND role = 'SUPER_ADMIN'",
				Integer.class);
		Integer campaignCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM kuji_campaigns", Integer.class);

		assertThat(adminCount).isEqualTo(1);
		assertThat(campaignCount).isEqualTo(3);
	}

	@Test
	void seedTicketTotalsMatchPrizeQuantities() {
		List<Map<String, Object>> mismatches = jdbcTemplate.queryForList("""
				SELECT c.slug, c.total_tickets, c.remaining_tickets,
				       COALESCE(SUM(CASE WHEN p.is_last_prize = 0 THEN p.original_quantity ELSE 0 END), 0) AS prize_total,
				       COALESCE(SUM(CASE WHEN p.is_last_prize = 0 THEN p.remaining_quantity ELSE 0 END), 0) AS prize_remaining,
				       (SELECT COUNT(*) FROM kuji_tickets t WHERE t.campaign_id = c.id) AS ticket_total
				FROM kuji_campaigns c
				LEFT JOIN prizes p ON p.campaign_id = c.id
				GROUP BY c.id
				HAVING c.total_tickets <> prize_total
				    OR c.remaining_tickets <> prize_remaining
				    OR c.total_tickets <> ticket_total
				""");

		assertThat(mismatches).isEmpty();
	}
}
