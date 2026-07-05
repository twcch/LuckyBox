package com.luckybox.checkin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
		"luckybox.promo.daily-check-in-bonus=20",
		"luckybox.promo.daily-check-in-streak-bonuses=3:30,7:80"
})
class CheckInApiTests {

	private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void deleteCheckInTestData() {
		jdbcTemplate.update("""
				DELETE FROM wallet_ledger
				WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'checkin-user-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM daily_check_ins
				WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'checkin-user-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM wallets
				WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'checkin-user-%')
				""");
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'checkin-user-%'");
	}

	@Test
	void statusReportsNotCheckedInForNewMember() throws Exception {
		TestUser user = registerUser();

		mockMvc.perform(get("/api/account/check-in").session(user.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.checkedInToday").value(false))
				.andExpect(jsonPath("$.rewardAmount").value(20))
				.andExpect(jsonPath("$.baseRewardAmount").value(20))
				.andExpect(jsonPath("$.streakBonusAmount").value(0))
				.andExpect(jsonPath("$.currentStreak").value(0))
				.andExpect(jsonPath("$.totalCheckIns").value(0))
				.andExpect(jsonPath("$.nextStreakBonusAt").value(3))
				.andExpect(jsonPath("$.nextStreakBonusAmount").value(30))
				.andExpect(jsonPath("$.daysUntilNextStreakBonus").value(2))
				.andExpect(jsonPath("$.today").exists());
	}

	@Test
	void checkInGrantsBonusOncePerDayAndIsIdempotent() throws Exception {
		TestUser user = registerUser();

		mockMvc.perform(post("/api/account/check-in").session(user.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.justCheckedIn").value(true))
				.andExpect(jsonPath("$.awardedAmount").value(20))
				.andExpect(jsonPath("$.status.checkedInToday").value(true))
				.andExpect(jsonPath("$.status.baseRewardAmount").value(20))
				.andExpect(jsonPath("$.status.streakBonusAmount").value(0))
				.andExpect(jsonPath("$.status.currentStreak").value(1))
				.andExpect(jsonPath("$.status.totalCheckIns").value(1))
				.andExpect(jsonPath("$.status.nextStreakBonusAt").value(3))
				.andExpect(jsonPath("$.status.daysUntilNextStreakBonus").value(2));

		// 第二次簽到（同日）應冪等：不再發點、總天數不變。
		mockMvc.perform(post("/api/account/check-in").session(user.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.justCheckedIn").value(false))
				.andExpect(jsonPath("$.awardedAmount").value(0))
				.andExpect(jsonPath("$.status.checkedInToday").value(true))
				.andExpect(jsonPath("$.status.totalCheckIns").value(1));

		Integer checkInRows = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM daily_check_ins
				WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'checkin-user-%')
				""", Integer.class);
		assertThat(checkInRows).isEqualTo(1);

		Integer bonusLedgerRows = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM wallet_ledger
				WHERE type = 'CHECK_IN_BONUS'
				  AND user_id IN (SELECT id FROM users WHERE email LIKE 'checkin-user-%')
				""", Integer.class);
		assertThat(bonusLedgerRows).isEqualTo(1);

		Integer bonusBalance = jdbcTemplate.queryForObject("""
				SELECT bonus_point_balance FROM wallets
				WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'checkin-user-%')
				""", Integer.class);
		assertThat(bonusBalance).isEqualTo(20);
	}

	@Test
	void consecutiveCheckInGrantsConfiguredStreakBonus() throws Exception {
		TestUser user = registerUser();
		LocalDate today = LocalDate.now(TAIPEI);
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO daily_check_ins (user_id, check_in_date, reward_amount, point_kind, created_at)
				VALUES (?, ?, 20, 'BONUS', ?)
				""", user.id(), today.minusDays(2).toString(), now);
		jdbcTemplate.update("""
				INSERT INTO daily_check_ins (user_id, check_in_date, reward_amount, point_kind, created_at)
				VALUES (?, ?, 20, 'BONUS', ?)
				""", user.id(), today.minusDays(1).toString(), now);

		mockMvc.perform(get("/api/account/check-in").session(user.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.checkedInToday").value(false))
				.andExpect(jsonPath("$.rewardAmount").value(50))
				.andExpect(jsonPath("$.baseRewardAmount").value(20))
				.andExpect(jsonPath("$.streakBonusAmount").value(30))
				.andExpect(jsonPath("$.currentStreak").value(2))
				.andExpect(jsonPath("$.totalCheckIns").value(2))
				.andExpect(jsonPath("$.nextStreakBonusAt").value(3))
				.andExpect(jsonPath("$.nextStreakBonusAmount").value(30))
				.andExpect(jsonPath("$.daysUntilNextStreakBonus").value(0));

		mockMvc.perform(post("/api/account/check-in").session(user.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.justCheckedIn").value(true))
				.andExpect(jsonPath("$.awardedAmount").value(50))
				.andExpect(jsonPath("$.status.checkedInToday").value(true))
				.andExpect(jsonPath("$.status.rewardAmount").value(50))
				.andExpect(jsonPath("$.status.baseRewardAmount").value(20))
				.andExpect(jsonPath("$.status.streakBonusAmount").value(30))
				.andExpect(jsonPath("$.status.currentStreak").value(3))
				.andExpect(jsonPath("$.status.totalCheckIns").value(3))
				.andExpect(jsonPath("$.status.nextStreakBonusAt").value(7))
				.andExpect(jsonPath("$.status.nextStreakBonusAmount").value(80))
				.andExpect(jsonPath("$.status.daysUntilNextStreakBonus").value(4));

		Integer todayReward = jdbcTemplate.queryForObject("""
				SELECT reward_amount
				FROM daily_check_ins
				WHERE user_id = ? AND check_in_date = ?
				""", Integer.class, user.id(), today.toString());
		assertThat(todayReward).isEqualTo(50);

		Integer bonusBalance = jdbcTemplate.queryForObject("""
				SELECT bonus_point_balance FROM wallets WHERE user_id = ?
				""", Integer.class, user.id());
		assertThat(bonusBalance).isEqualTo(50);

		Integer ledgerAmount = jdbcTemplate.queryForObject("""
				SELECT amount
				FROM wallet_ledger
				WHERE user_id = ? AND type = 'CHECK_IN_BONUS'
				""", Integer.class, user.id());
		assertThat(ledgerAmount).isEqualTo(50);
	}

	private TestUser registerUser() throws Exception {
		String email = "checkin-user-" + UUID.randomUUID() + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{
								  "email": "%s",
								  "password": "Password123!",
								  "displayName": "簽到測試員"
								}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();
		Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
		return new TestUser(email, userId, (MockHttpSession) result.getRequest().getSession(false));
	}

	private record TestUser(String email, long id, MockHttpSession session) {
	}
}
