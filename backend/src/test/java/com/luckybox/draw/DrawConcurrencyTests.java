package com.luckybox.draw;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.luckybox.account.AuthUser;
import com.luckybox.account.SessionPrincipal;

@SpringBootTest
class DrawConcurrencyTests {

	@Autowired
	private DrawService drawService;

	@Autowired
	private DrawRepository drawRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("DELETE FROM wallet_ledger WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'conc-test-%@example.com')");
		jdbcTemplate.update("DELETE FROM user_prizes WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'conc-test-%')");
		jdbcTemplate.update("DELETE FROM draw_results WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'conc-test-%')");
		jdbcTemplate.update("DELETE FROM draw_orders WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'conc-test-%')");
		jdbcTemplate.update("DELETE FROM kuji_tickets WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'conc-test-%')");
		jdbcTemplate.update("DELETE FROM prizes WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'conc-test-%')");
		jdbcTemplate.update("DELETE FROM kuji_campaigns WHERE slug LIKE 'conc-test-%'");
		jdbcTemplate.update("DELETE FROM wallets WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'conc-test-%@example.com')");
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'conc-test-%@example.com'");
	}

	// End-to-end check that 100 concurrently-submitted draws yield correct results in the PRODUCTION config
	// (in-process lock on, pool size 1) — exactly N succeed, no duplicate ticket, no oversell, balance correct.
	// Note: under that config draws are effectively serialized, so this does NOT by itself prove the DB-layer
	// guards work under true contention — that guarantee is proven separately and lock-independently by
	// conditionalUpdateRejectsDoubleDrawOfSameTicket below.
	@Test
	void hundredConcurrentDrawsNeverOversellOrDuplicate() throws Exception {
		int ticketCount = 40;
		int attempts = 100;
		int initialBalance = 1_000_000;
		long userId = insertUser(initialBalance);
		String slug = seedCampaign(ticketCount);
		Authentication auth = authenticationFor(userId);

		ExecutorService pool = Executors.newFixedThreadPool(attempts);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Boolean>> futures = new ArrayList<>();
		int success = 0;
		try {
			for (int i = 0; i < attempts; i++) {
				futures.add(pool.submit(() -> {
					start.await();
					SecurityContextHolder.getContext().setAuthentication(auth);
					try {
						drawService.createDrawOrder(new CreateDrawOrderRequest(slug, 1, UUID.randomUUID().toString()));
						return true;
					} catch (RuntimeException ex) {
						return false;
					} finally {
						SecurityContextHolder.clearContext();
					}
				}));
			}
			start.countDown();
			for (Future<Boolean> future : futures) {
				if (Boolean.TRUE.equals(future.get())) {
					success++;
				}
			}
		} finally {
			pool.shutdownNow();
		}

		assertThat(success).isEqualTo(ticketCount);

		Long drawnTickets = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM kuji_tickets
				WHERE campaign_id = (SELECT id FROM kuji_campaigns WHERE slug = ?) AND status = 'DRAWN'
				""", Long.class, slug);
		Long totalResults = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM draw_results
				WHERE campaign_id = (SELECT id FROM kuji_campaigns WHERE slug = ?)
				""", Long.class, slug);
		Long distinctTickets = jdbcTemplate.queryForObject("""
				SELECT COUNT(DISTINCT ticket_id) FROM draw_results
				WHERE campaign_id = (SELECT id FROM kuji_campaigns WHERE slug = ?)
				""", Long.class, slug);
		Integer remaining = jdbcTemplate.queryForObject("SELECT remaining_tickets FROM kuji_campaigns WHERE slug = ?", Integer.class, slug);
		String status = jdbcTemplate.queryForObject("SELECT status FROM kuji_campaigns WHERE slug = ?", String.class, slug);
		Integer balance = jdbcTemplate.queryForObject("""
				SELECT cash_point_balance FROM wallets WHERE user_id = ?
				""", Integer.class, userId);

		assertThat(drawnTickets).isEqualTo((long) ticketCount);
		assertThat(totalResults).isEqualTo((long) ticketCount);
		assertThat(distinctTickets).isEqualTo((long) ticketCount);
		assertThat(remaining).isZero();
		assertThat(status).isEqualTo("SOLD_OUT");
		assertThat(balance).isEqualTo(initialBalance - ticketCount);
	}

	@Test
	void conditionalUpdateRejectsDoubleDrawOfSameTicket() {
		long userId = insertUser(1_000);
		String slug = seedCampaign(1);
		Long ticketId = jdbcTemplate.queryForObject("""
				SELECT id FROM kuji_tickets
				WHERE campaign_id = (SELECT id FROM kuji_campaigns WHERE slug = ?) LIMIT 1
				""", Long.class, slug);
		Long prizeId = jdbcTemplate.queryForObject("SELECT prize_id FROM kuji_tickets WHERE id = ?", Long.class, ticketId);
		DrawRepository.TicketRow ticket = new DrawRepository.TicketRow(
				ticketId == null ? 0 : ticketId, prizeId == null ? 0 : prizeId, "conc-guard", "A", "壓測賞", false);

		boolean first = drawRepository.markTicketDrawn(ticket, 0L, userId);
		boolean second = drawRepository.markTicketDrawn(ticket, 0L, userId);

		assertThat(first).isTrue();
		assertThat(second).isFalse();
	}

	@Test
	void deductCashPointsRejectsAndWritesNothingWhenBalanceTooLow() {
		long userId = insertUser(10);
		Long walletId = jdbcTemplate.queryForObject("SELECT id FROM wallets WHERE user_id = ?", Long.class, userId);

		boolean ok = drawRepository.deductCashPoints(userId, walletId == null ? 0 : walletId, 0L, 20);

		assertThat(ok).isFalse();
		Integer balance = jdbcTemplate.queryForObject("SELECT cash_point_balance FROM wallets WHERE user_id = ?", Integer.class, userId);
		Integer ledgerRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM wallet_ledger WHERE user_id = ?", Integer.class, userId);
		assertThat(balance).isEqualTo(10);
		assertThat(ledgerRows).isZero();
	}

	private Authentication authenticationFor(long userId) {
		AuthUser authUser = new AuthUser(userId, "conc@example.com", "壓測玩家", null, "USER", "ACTIVE", "REGULAR", 0, 0);
		SessionPrincipal principal = new SessionPrincipal(authUser);
		return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
	}

	private long insertUser(int cashBalance) {
		String now = Instant.now().toString();
		String email = "conc-test-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
		jdbcTemplate.update("""
				INSERT INTO users (
					email, phone, password_hash, display_name, avatar_url, role, status, vip_level,
					created_at, updated_at, last_login_at
				)
				VALUES (?, NULL, 'x', '壓測玩家', NULL, 'USER', 'ACTIVE', 'REGULAR', ?, ?, NULL)
				""", email, now, now);
		Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
		long resolvedUserId = userId == null ? 0 : userId;
		jdbcTemplate.update("""
				INSERT INTO wallets (user_id, cash_point_balance, bonus_point_balance, locked_balance, created_at, updated_at)
				VALUES (?, ?, 0, 0, ?, ?)
				""", resolvedUserId, cashBalance, now, now);
		return resolvedUserId;
	}

	private String seedCampaign(int ticketCount) {
		String slug = "conc-test-" + UUID.randomUUID().toString().substring(0, 8);
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO kuji_campaigns (
					slug, title, subtitle, description, cover_image_url, banner_image_url, source_type,
					ip_name, brand_name, price_per_draw, total_tickets, remaining_tickets, status,
					sales_start_at, sales_end_at, shipping_note, return_policy_note, has_last_prize,
					last_prize_rule, fairness_mode, seed_hash, revealed_seed, created_at, updated_at
				)
				VALUES (?, ?, ?, ?, NULL, NULL, 'MIXED', NULL, 'LuckyBox Test', 1, ?, ?, 'LIVE',
					?, NULL, '測試出貨', '測試退換貨', 0, NULL, 'SERVER_RANDOM', ?, NULL, ?, ?)
				""", slug, "併發壓測池", "併發壓測", "併發壓測資料", ticketCount, ticketCount, now, "conc-seed-" + slug, now, now);
		Long campaignId = jdbcTemplate.queryForObject("SELECT id FROM kuji_campaigns WHERE slug = ?", Long.class, slug);
		long resolvedCampaignId = campaignId == null ? 0 : campaignId;
		jdbcTemplate.update("""
				INSERT INTO prizes (
					campaign_id, rank, name, description, image_url, original_quantity, remaining_quantity,
					sort_order, is_last_prize, created_at, updated_at
				)
				VALUES (?, 'A', '壓測賞', '併發測試獎品', NULL, ?, ?, 1, 0, ?, ?)
				""", resolvedCampaignId, ticketCount, ticketCount, now, now);
		Long prizeId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		long resolvedPrizeId = prizeId == null ? 0 : prizeId;
		for (int index = 1; index <= ticketCount; index++) {
			jdbcTemplate.update("""
					INSERT INTO kuji_tickets (
						campaign_id, prize_id, serial_number, status, draw_id, drawn_by_user_id, drawn_at, created_at, updated_at
					)
					VALUES (?, ?, ?, 'AVAILABLE', NULL, NULL, NULL, ?, ?)
					""", resolvedCampaignId, resolvedPrizeId, slug.toUpperCase() + "-" + String.format("%04d", index), now, now);
		}
		return slug;
	}
}
