package com.luckybox.dev;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.luckybox.audit.AuditLogHelper;

// Dev/test data seeding. Never runs under the "prod" profile, so a production deployment started with
// SPRING_PROFILES_ACTIVE=prod will not create demo users/admins/campaigns even if the seed flag is left on.
@Configuration
@Profile("!prod")
class DevSeedRunner {

	private static final Logger log = LoggerFactory.getLogger(DevSeedRunner.class);

	@Bean
	ApplicationRunner seedDevelopmentData(DevSeedService seedService, @Value("${luckybox.seed.enabled:true}") boolean enabled) {
		return args -> {
			if (!enabled) {
				log.info("Dev seed is disabled");
				return;
			}
			seedService.seed();
		};
	}

		@Bean
		DevSeedService devSeedService(JdbcTemplate jdbcTemplate, AuditLogHelper auditLogHelper, PasswordEncoder passwordEncoder,
				PlatformTransactionManager transactionManager) {
			return new DevSeedService(jdbcTemplate, auditLogHelper, passwordEncoder, new TransactionTemplate(transactionManager));
		}

	static class DevSeedService {

			private final JdbcTemplate jdbcTemplate;
			private final AuditLogHelper auditLogHelper;
			private final PasswordEncoder passwordEncoder;
			private final TransactionTemplate transactionTemplate;

			DevSeedService(JdbcTemplate jdbcTemplate, AuditLogHelper auditLogHelper, PasswordEncoder passwordEncoder,
					TransactionTemplate transactionTemplate) {
				this.jdbcTemplate = jdbcTemplate;
				this.auditLogHelper = auditLogHelper;
				this.passwordEncoder = passwordEncoder;
				this.transactionTemplate = transactionTemplate;
			}

		void seed() {
			int seededCampaigns = transactionTemplate.execute(status -> doSeed());
			log.info("Dev seed completed: {} new campaigns seeded", seededCampaigns);
		}

		private int doSeed() {
			if (!TransactionSynchronizationManager.isActualTransactionActive()) {
				throw new IllegalStateException("Dev seed must run inside an active transaction");
			}
			long adminId = seedAdmin();
			int seededCampaigns = 0;
			for (CampaignSeed campaign : campaigns()) {
				if (campaignExists(campaign.slug())) {
					continue;
				}
				long campaignId = insertCampaign(campaign);
				insertPrizesAndTickets(campaignId, campaign);
				auditLogHelper.recordSystemAction(
						"DEV_SEED_CAMPAIGN",
						"KujiCampaign",
						String.valueOf(campaignId),
						"{\"slug\":\"" + campaign.slug() + "\",\"adminId\":" + adminId + "}");
				seededCampaigns++;
			}
			return seededCampaigns;
		}

		private long seedAdmin() {
			Long existingId = jdbcTemplate.query("""
					SELECT id FROM users WHERE email = ?
					""", rs -> rs.next() ? rs.getLong("id") : null, "admin@luckybox.local");
			if (existingId != null) {
				return existingId;
			}

			String now = Instant.now().toString();
			jdbcTemplate.update("""
					INSERT INTO users (
						email, phone, password_hash, display_name, avatar_url, role, status, vip_level,
						created_at, updated_at, last_login_at
					)
					VALUES (?, NULL, ?, ?, NULL, 'SUPER_ADMIN', 'ACTIVE', 'REGULAR', ?, ?, NULL)
					""",
					"admin@luckybox.local",
					passwordEncoder.encode("ChangeMe123!"),
					"LuckyBox Admin",
					now,
					now);
			Long adminId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, "admin@luckybox.local");
			auditLogHelper.recordSystemAction(
					"DEV_SEED_ADMIN",
					"User",
					String.valueOf(adminId),
					"{\"email\":\"admin@luckybox.local\",\"role\":\"SUPER_ADMIN\"}");
			return adminId == null ? 0 : adminId;
		}

		private boolean campaignExists(String slug) {
			Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM kuji_campaigns WHERE slug = ?", Integer.class, slug);
			return count != null && count > 0;
		}

		private long insertCampaign(CampaignSeed campaign) {
			String now = Instant.now().toString();
			int totalTickets = campaign.totalTicketQuantity();
			jdbcTemplate.update("""
					INSERT INTO kuji_campaigns (
						slug, title, subtitle, description, cover_image_url, banner_image_url, source_type,
						ip_name, brand_name, price_per_draw, total_tickets, remaining_tickets, status,
						sales_start_at, sales_end_at, shipping_note, return_policy_note, has_last_prize,
						last_prize_rule, fairness_mode, seed_hash, revealed_seed, created_at, updated_at
					)
					VALUES (?, ?, ?, ?, NULL, NULL, ?, NULL, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, 'SERVER_RANDOM', ?, NULL, ?, ?)
					""",
					campaign.slug(),
					campaign.title(),
					campaign.subtitle(),
					campaign.description(),
					campaign.sourceType(),
					campaign.brandName(),
					campaign.pricePerDraw(),
					totalTickets,
					totalTickets,
					campaign.status(),
					now,
					campaign.shippingNote(),
					campaign.returnPolicyNote(),
					campaign.hasLastPrize() ? 1 : 0,
					campaign.lastPrizeRule(),
					"phase1-dev-seed-" + campaign.slug(),
					now,
					now);
			Long campaignId = jdbcTemplate.queryForObject("SELECT id FROM kuji_campaigns WHERE slug = ?", Long.class, campaign.slug());
			return campaignId == null ? 0 : campaignId;
		}

		private void insertPrizesAndTickets(long campaignId, CampaignSeed campaign) {
			int ticketSerial = 1;
			int sortOrder = 1;
			for (PrizeSeed prize : campaign.prizes()) {
				long prizeId = insertPrize(campaignId, prize, sortOrder++);
				if (prize.lastPrize()) {
					continue;
				}
				for (int index = 0; index < prize.quantity(); index++) {
					insertTicket(campaignId, prizeId, campaign.slug(), ticketSerial++);
				}
			}
		}

		private long insertPrize(long campaignId, PrizeSeed prize, int sortOrder) {
			String now = Instant.now().toString();
			jdbcTemplate.update("""
					INSERT INTO prizes (
						campaign_id, rank, name, description, image_url, original_quantity, remaining_quantity,
						sort_order, is_last_prize, created_at, updated_at
					)
					VALUES (?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?)
					""",
					campaignId,
					prize.rank(),
					prize.name(),
					prize.description(),
					prize.quantity(),
					prize.quantity(),
					sortOrder,
					prize.lastPrize() ? 1 : 0,
					now,
					now);
			Long prizeId = jdbcTemplate.queryForObject("""
					SELECT id FROM prizes WHERE campaign_id = ? AND rank = ? AND sort_order = ?
					""", Long.class, campaignId, prize.rank(), sortOrder);
			return prizeId == null ? 0 : prizeId;
		}

		private void insertTicket(long campaignId, long prizeId, String slug, int ticketSerial) {
			String now = Instant.now().toString();
			jdbcTemplate.update("""
					INSERT INTO kuji_tickets (
						campaign_id, prize_id, serial_number, status, draw_id, drawn_by_user_id, drawn_at, created_at, updated_at
					)
					VALUES (?, ?, ?, 'AVAILABLE', NULL, NULL, NULL, ?, ?)
					""", campaignId, prizeId, slug.toUpperCase().replace('-', '_') + "-" + String.format("%04d", ticketSerial), now, now);
		}

		private static List<CampaignSeed> campaigns() {
			return List.of(
					new CampaignSeed(
							"star-collection-vol-1",
							"星光收藏盒 Vol.1",
							"透明剩餘數的入門測試賞池",
							"無授權 IP 的測試賞池，用來驗證公開剩餘數、獎項機率與售後資訊呈現。",
							"MIXED",
							"LuckyBox Test",
							100,
							"LIVE",
							"現貨商品預計 3 到 5 個工作天安排出貨，可合併戰利品出貨。",
							"商品拆封後如非瑕疵不接受退換，瑕疵請於收貨 7 日內聯繫客服。",
							true,
							"最後一張普通 ticket 被抽出時，該筆抽賞額外獲得最後賞。",
							List.of(
									new PrizeSeed("S", "星光大賞收藏盒", "稀有展示盒測試獎項。", 1, false),
									new PrizeSeed("A", "壓克力展示組", "桌面展示用測試獎項。", 2, false),
									new PrizeSeed("B", "收藏小物組", "實用收藏配件。", 7, false),
									new PrizeSeed("C", "造型卡套", "卡牌保護與展示。", 20, false),
									new PrizeSeed("D", "LuckyBox 貼紙包", "入門測試獎項。", 50, false),
									new PrizeSeed("LAST", "星光最後賞", "售完時觸發的額外測試獎項。", 1, true))),
					new CampaignSeed(
							"card-supply-selection",
							"卡牌補給包精選",
							"卡牌收藏玩家測試池",
							"以卡牌周邊為主的測試賞池，展示不同等級獎項與即時機率。",
							"CARD",
							"LuckyBox Test",
							150,
							"LIVE",
							"卡牌類商品會以硬殼或防折包材出貨。",
							"卡牌品項可能存在製程細紋，重大瑕疵依客服判定補償或換貨。",
							false,
							null,
							List.of(
									new PrizeSeed("S", "高階卡盒組", "高稀有測試獎項。", 1, false),
									new PrizeSeed("A", "收藏冊", "可收納卡牌。", 3, false),
									new PrizeSeed("B", "硬殼卡磚", "展示與保護。", 8, false),
									new PrizeSeed("C", "卡套組", "常用保護耗材。", 18, false),
									new PrizeSeed("D", "補充小包", "基本測試獎項。", 30, false))),
					new CampaignSeed(
							"blind-box-party",
							"盲盒派對測試池",
							"行動端友善的盲盒池",
							"用於平台展示的盲盒類賞池，聚焦透明資訊與穩定瀏覽。",
							"BLIND_BOX",
							"LuckyBox Test",
							80,
							"SCHEDULED",
							"預計開抽後依出貨批次寄送。",
							"盲盒內容不接受指定款式，瑕疵依平台規則處理。",
							true,
							"完抽時由最後一筆成功抽賞取得最後賞。",
							List.of(
									new PrizeSeed("A", "派對主賞", "主要造型盲盒測試獎項。", 4, false),
									new PrizeSeed("B", "隱藏配色小物", "中稀有測試獎項。", 16, false),
									new PrizeSeed("C", "桌面小擺件", "標準盲盒獎項。", 30, false),
									new PrizeSeed("D", "收藏小卡", "基礎盲盒獎項。", 50, false),
									new PrizeSeed("LAST", "派對最後賞", "售完時觸發的額外測試獎項。", 1, true))));
		}
	}

	private record CampaignSeed(
			String slug,
			String title,
			String subtitle,
			String description,
			String sourceType,
			String brandName,
			int pricePerDraw,
			String status,
			String shippingNote,
			String returnPolicyNote,
			boolean hasLastPrize,
			String lastPrizeRule,
			List<PrizeSeed> prizes) {

		int totalTicketQuantity() {
			return prizes.stream()
					.filter(prize -> !prize.lastPrize())
					.mapToInt(PrizeSeed::quantity)
					.sum();
		}
	}

	private record PrizeSeed(
			String rank,
			String name,
			String description,
			int quantity,
			boolean lastPrize) {
	}
}
