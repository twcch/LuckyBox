package com.luckybox.admin.user;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.luckybox.common.PiiMasking;

@Repository
class AdminUserRepository {

	private final JdbcTemplate jdbcTemplate;

	AdminUserRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	List<AdminUserResponse> findUsers(String status, String role, String keyword) {
		List<Object> params = new ArrayList<>();
		StringBuilder where = new StringBuilder("WHERE 1 = 1\n");
		if (status != null) {
			where.append("AND u.status = ?\n");
			params.add(status);
		}
		if (role != null) {
			where.append("AND u.role = ?\n");
			params.add(role);
		}
		if (keyword != null) {
			where.append("""
					AND (
						lower(u.email) LIKE ?
						OR lower(u.display_name) LIKE ?
						OR lower(COALESCE(u.phone, '')) LIKE ?
					)
					""");
			String like = "%" + keyword.toLowerCase() + "%";
			params.add(like);
			params.add(like);
			params.add(like);
		}
		return jdbcTemplate.query("""
				SELECT
					u.id, u.email, u.display_name, u.phone, u.role, u.status, u.vip_level,
					u.created_at, u.last_login_at,
					COALESCE(w.cash_point_balance, 0) AS cash_point_balance,
					COALESCE(w.bonus_point_balance, 0) AS bonus_point_balance,
					(
						SELECT COUNT(*)
						FROM draw_orders d
						WHERE d.user_id = u.id
					) AS draw_order_count,
					(
						SELECT COUNT(*)
						FROM user_prizes p
						WHERE p.user_id = u.id
					) AS prize_count,
					(
						SELECT COUNT(*)
						FROM shipments s
						WHERE s.user_id = u.id
					) AS shipment_count
				FROM users u
				LEFT JOIN wallets w ON w.user_id = u.id
				""" + where + """
				ORDER BY u.id DESC
				LIMIT 200
				""", (rs, rowNum) -> mapUser(rs), params.toArray());
	}

	UserRow findUser(long userId) {
		List<UserRow> users = jdbcTemplate.query("""
				SELECT id, role, status
				FROM users
				WHERE id = ?
				""", (rs, rowNum) -> new UserRow(
				rs.getLong("id"),
				rs.getString("role"),
				rs.getString("status")), userId);
		return users.stream().findFirst().orElse(null);
	}

	boolean updateStatus(long userId, String status) {
		String now = Instant.now().toString();
		return jdbcTemplate.update("""
				UPDATE users
				SET status = ?, updated_at = ?
				WHERE id = ?
				""", status, now, userId) > 0;
	}

	boolean updateRole(long userId, String role) {
		String now = Instant.now().toString();
		return jdbcTemplate.update("""
				UPDATE users
				SET role = ?, updated_at = ?
				WHERE id = ?
				""", role, now, userId) > 0;
	}

	AdminUserResponse findResponse(long userId) {
		List<AdminUserResponse> users = jdbcTemplate.query("""
				SELECT
					u.id, u.email, u.display_name, u.phone, u.role, u.status, u.vip_level,
					u.created_at, u.last_login_at,
					COALESCE(w.cash_point_balance, 0) AS cash_point_balance,
					COALESCE(w.bonus_point_balance, 0) AS bonus_point_balance,
					(
						SELECT COUNT(*)
						FROM draw_orders d
						WHERE d.user_id = u.id
					) AS draw_order_count,
					(
						SELECT COUNT(*)
						FROM user_prizes p
						WHERE p.user_id = u.id
					) AS prize_count,
					(
						SELECT COUNT(*)
						FROM shipments s
						WHERE s.user_id = u.id
					) AS shipment_count
				FROM users u
				LEFT JOIN wallets w ON w.user_id = u.id
				WHERE u.id = ?
				""", (rs, rowNum) -> mapUser(rs), userId);
		return users.stream().findFirst().orElse(null);
	}

	AdminMemberDetailResponse findMemberDetail(long userId, boolean revealPii) {
		List<AdminMemberDetailResponse> rows = jdbcTemplate.query("""
				SELECT
					u.id, u.email, u.display_name, u.phone, u.role, u.status, u.vip_level,
					u.created_at, u.last_login_at,
					COALESCE(w.cash_point_balance, 0) AS cash_point_balance,
					COALESCE(w.bonus_point_balance, 0) AS bonus_point_balance,
					COALESCE(w.locked_balance, 0) AS locked_balance,
					(SELECT COUNT(*) FROM draw_orders d WHERE d.user_id = u.id) AS draw_order_count,
					(SELECT COUNT(*) FROM draw_orders d WHERE d.user_id = u.id AND d.status = 'COMPLETED') AS completed_draw_count,
					(SELECT COALESCE(SUM(d.point_spent), 0) FROM draw_orders d WHERE d.user_id = u.id AND d.status = 'COMPLETED') AS total_draw_spend,
					(SELECT COUNT(*) FROM payment_orders po WHERE po.user_id = u.id AND po.status = 'PAID') AS paid_order_count,
					(SELECT COALESCE(SUM(po.amount), 0) FROM payment_orders po WHERE po.user_id = u.id AND po.status = 'PAID') AS paid_amount,
					(SELECT COUNT(*) FROM user_prizes p WHERE p.user_id = u.id) AS prize_count,
					(SELECT COUNT(*) FROM shipments s WHERE s.user_id = u.id) AS shipment_count
				FROM users u
				LEFT JOIN wallets w ON w.user_id = u.id
				WHERE u.id = ?
				""", (rs, rowNum) -> mapMemberDetail(rs, revealPii), userId);
		return rows.stream().findFirst().orElse(null);
	}

	private AdminMemberDetailResponse mapMemberDetail(ResultSet rs, boolean revealPii) throws SQLException {
		long id = rs.getLong("id");
		String role = rs.getString("role");
		String status = rs.getString("status");
		int cash = rs.getInt("cash_point_balance");
		int bonus = rs.getInt("bonus_point_balance");
		int locked = rs.getInt("locked_balance");
		return new AdminMemberDetailResponse(
				id,
				revealPii,
				revealPii ? rs.getString("email") : PiiMasking.maskEmail(rs.getString("email")),
				rs.getString("display_name"),
				revealPii ? rs.getString("phone") : PiiMasking.maskPhone(rs.getString("phone")),
				role,
				roleLabel(role),
				status,
				statusLabel(status),
				rs.getString("vip_level"),
				cash,
				bonus,
				locked,
				cash + bonus - locked,
				rs.getInt("draw_order_count"),
				rs.getInt("completed_draw_count"),
				rs.getInt("total_draw_spend"),
				rs.getInt("paid_order_count"),
				rs.getInt("paid_amount"),
				rs.getInt("prize_count"),
				rs.getInt("shipment_count"),
				rs.getString("created_at"),
				rs.getString("last_login_at"),
				loadAddresses(id, revealPii),
				loadRecentLedger(id),
				loadRecentPrizes(id),
				loadNotes(id));
	}

	List<AdminMemberDetailResponse.Note> loadNotes(long userId) {
		return jdbcTemplate.query("""
				SELECT n.id, n.content, COALESCE(a.display_name, '系統') AS author_name, n.created_at
				FROM member_notes n
				LEFT JOIN users a ON a.id = n.author_id
				WHERE n.user_id = ?
				ORDER BY n.id DESC
				LIMIT 50
				""", (rs, rowNum) -> new AdminMemberDetailResponse.Note(
				rs.getLong("id"),
				rs.getString("content"),
				rs.getString("author_name"),
				rs.getString("created_at")), userId);
	}

	long addMemberNote(long userId, long authorId, String content) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO member_notes (user_id, author_id, content, created_at)
				VALUES (?, ?, ?, ?)
				""", userId, authorId, content, now);
		Long id = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		return id == null ? 0 : id;
	}

	boolean userExists(long userId) {
		Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, userId);
		return count != null && count > 0;
	}

	/** 發放補償點：入紅利餘額並寫一筆 COMPENSATION 流水（point_kind=BONUS），回傳流水 id 與發放後紅利餘額。 */
	CompensationResponse grantCompensation(long userId, int amount, String reason, long adminId) {
		long walletId = ensureWallet(userId);
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				UPDATE wallets
				SET bonus_point_balance = bonus_point_balance + ?, updated_at = ?
				WHERE id = ? AND user_id = ?
				""", amount, now, walletId, userId);
		Integer balanceAfter = jdbcTemplate.queryForObject(
				"SELECT bonus_point_balance FROM wallets WHERE id = ? AND user_id = ?", Integer.class, walletId, userId);
		int resolvedBalance = balanceAfter == null ? 0 : balanceAfter;
		jdbcTemplate.update("""
				INSERT INTO wallet_ledger (
					user_id, wallet_id, type, amount, point_kind, balance_after,
					reference_type, reference_id, reason, created_by, created_at
				)
				VALUES (?, ?, 'COMPENSATION', ?, 'BONUS', ?, 'Compensation', NULL, ?, ?, ?)
				""", userId, walletId, amount, resolvedBalance, reason, adminId, now);
		Long ledgerId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		return new CompensationResponse(ledgerId == null ? 0 : ledgerId, amount, resolvedBalance);
	}

	private long ensureWallet(long userId) {
		Long existing = walletId(userId);
		if (existing != null) {
			return existing;
		}
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT OR IGNORE INTO wallets (
					user_id, cash_point_balance, bonus_point_balance, locked_balance, created_at, updated_at
				)
				VALUES (?, 0, 0, 0, ?, ?)
				""", userId, now, now);
		Long created = walletId(userId);
		if (created == null) {
			throw new IllegalStateException("Wallet was not created for user " + userId);
		}
		return created;
	}

	private Long walletId(long userId) {
		return jdbcTemplate.query("SELECT id FROM wallets WHERE user_id = ?", (rs, rowNum) -> rs.getLong("id"), userId)
				.stream().findFirst().orElse(null);
	}

	private List<AdminMemberDetailResponse.Address> loadAddresses(long userId, boolean revealPii) {
		return jdbcTemplate.query("""
				SELECT id, recipient_name, phone, postal_code, city, district, address_line, is_default
				FROM user_addresses
				WHERE user_id = ?
				ORDER BY is_default DESC, id DESC
				""", (rs, rowNum) -> new AdminMemberDetailResponse.Address(
				rs.getLong("id"),
				revealPii ? rs.getString("recipient_name") : PiiMasking.maskName(rs.getString("recipient_name")),
				revealPii ? rs.getString("phone") : PiiMasking.maskPhone(rs.getString("phone")),
				rs.getString("postal_code"),
				rs.getString("city"),
				rs.getString("district"),
				revealPii ? rs.getString("address_line") : PiiMasking.maskAddressLine(rs.getString("address_line")),
				rs.getInt("is_default") == 1), userId);
	}

	private List<AdminMemberDetailResponse.LedgerEntry> loadRecentLedger(long userId) {
		return jdbcTemplate.query("""
				SELECT id, type, amount, point_kind, balance_after, reason, created_at
				FROM wallet_ledger
				WHERE user_id = ?
				ORDER BY id DESC
				LIMIT 10
				""", (rs, rowNum) -> new AdminMemberDetailResponse.LedgerEntry(
				rs.getLong("id"),
				rs.getString("type"),
				ledgerTypeLabel(rs.getString("type")),
				rs.getInt("amount"),
				rs.getString("point_kind"),
				rs.getInt("balance_after"),
				rs.getString("reason"),
				rs.getString("created_at")), userId);
	}

	private List<AdminMemberDetailResponse.PrizeItem> loadRecentPrizes(long userId) {
		return jdbcTemplate.query("""
				SELECT
					up.id, c.slug, c.title, p.rank, p.name,
					t.serial_number, up.status, up.shipment_id, up.created_at
				FROM user_prizes up
				JOIN kuji_campaigns c ON c.id = up.campaign_id
				JOIN prizes p ON p.id = up.prize_id
				LEFT JOIN draw_results dr ON dr.id = up.draw_result_id
				LEFT JOIN kuji_tickets t ON t.id = dr.ticket_id
				WHERE up.user_id = ?
				ORDER BY up.id DESC
				LIMIT 20
				""", (rs, rowNum) -> {
			Long shipmentId = rs.getObject("shipment_id") == null ? null : rs.getLong("shipment_id");
			String status = rs.getString("status");
			return new AdminMemberDetailResponse.PrizeItem(
					rs.getLong("id"),
					rs.getString("slug"),
					rs.getString("title"),
					rs.getString("rank"),
					rs.getString("name"),
					rs.getString("serial_number"),
					status,
					prizeStatusLabel(status),
					shipmentId,
					rs.getString("created_at"));
		}, userId);
	}

	private static String ledgerTypeLabel(String type) {
		return switch (type) {
			case "TOP_UP" -> "現金儲值";
			case "TOP_UP_BONUS" -> "儲值贈點";
			case "COUPON_BONUS" -> "優惠券贈點";
			case "FIRST_DEPOSIT_BONUS" -> "首儲贈點";
			case "SPEND_THRESHOLD_BONUS" -> "消費門檻紅利";
			case "CHECK_IN_BONUS" -> "每日簽到獎勵";
			case "DRAW_SPEND" -> "抽賞扣點";
			case "ADJUSTMENT" -> "人工調整";
			case "REFUND" -> "退款回補";
			case "COMPENSATION" -> "客服補償";
			default -> type;
		};
	}

	private static String prizeStatusLabel(String status) {
		return switch (status) {
			case "IN_BOX" -> "可出貨";
			case "SHIPMENT_REQUESTED" -> "已申請出貨";
			case "SHIPPED" -> "已出貨";
			case "DELIVERED" -> "已送達";
			case "EXCHANGED" -> "換貨處理";
			default -> status;
		};
	}

	private static AdminUserResponse mapUser(ResultSet rs) throws SQLException {
		String role = rs.getString("role");
		String status = rs.getString("status");
		return new AdminUserResponse(
				rs.getLong("id"),
				maskEmail(rs.getString("email")),
				rs.getString("display_name"),
				maskPhone(rs.getString("phone")),
				role,
				roleLabel(role),
				status,
				statusLabel(status),
				rs.getString("vip_level"),
				rs.getInt("cash_point_balance"),
				rs.getInt("bonus_point_balance"),
				rs.getInt("draw_order_count"),
				rs.getInt("prize_count"),
				rs.getInt("shipment_count"),
				rs.getString("created_at"),
				rs.getString("last_login_at"));
	}

	private static String maskEmail(String email) {
		if (email == null || email.isBlank()) {
			return "";
		}
		int at = email.indexOf('@');
		if (at <= 0) {
			return maskText(email, 2, 0);
		}
		String local = email.substring(0, at);
		String domain = email.substring(at + 1);
		return maskText(local, 2, 0) + "@" + maskDomain(domain);
	}

	private static String maskDomain(String domain) {
		if (domain == null || domain.isBlank()) {
			return "***";
		}
		int dot = domain.indexOf('.');
		if (dot <= 0) {
			return maskText(domain, 1, 0);
		}
		return maskText(domain.substring(0, dot), 1, 0) + domain.substring(dot);
	}

	private static String maskPhone(String phone) {
		if (phone == null || phone.isBlank()) {
			return "";
		}
		return maskText(phone, 0, Math.min(3, phone.length()));
	}

	private static String maskText(String value, int visiblePrefix, int visibleSuffix) {
		if (value == null || value.isBlank()) {
			return "";
		}
		if (value.length() <= visiblePrefix + visibleSuffix) {
			return "*".repeat(value.length());
		}
		String prefix = value.substring(0, Math.min(visiblePrefix, value.length()));
		String suffix = visibleSuffix == 0 ? "" : value.substring(value.length() - visibleSuffix);
		return prefix + "***" + suffix;
	}

	private static String roleLabel(String role) {
		return switch (role) {
			case "SUPER_ADMIN" -> "超級管理員";
			case "ADMIN" -> "管理員";
			case "OPERATOR" -> "營運";
			case "CUSTOMER_SERVICE" -> "客服";
			case "USER" -> "會員";
			default -> role;
		};
	}

	private static String statusLabel(String status) {
		return switch (status) {
			case "ACTIVE" -> "啟用";
			case "SUSPENDED" -> "停權";
			case "DELETED" -> "已刪除";
			default -> status;
		};
	}

	record UserRow(long id, String role, String status) {
	}
}
