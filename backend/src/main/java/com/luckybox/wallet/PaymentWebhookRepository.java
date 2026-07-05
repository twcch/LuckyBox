package com.luckybox.wallet;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class PaymentWebhookRepository {

	private final JdbcTemplate jdbcTemplate;

	PaymentWebhookRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	boolean insertEvent(String provider, PaymentWebhookRequest request, String rawPayload) {
		return insertEvent(provider, request.eventId(), request.merchantTradeNo(), request.status(), request.amount(), rawPayload);
	}

	boolean insertEvent(String provider, String eventId, String merchantTradeNo, String status, int amount, String rawPayload) {
		int rows = jdbcTemplate.update("""
				INSERT OR IGNORE INTO payment_webhook_events (
					provider, event_id, merchant_trade_no, status, amount,
					raw_payload, processed, message, created_at, processed_at
				)
				VALUES (?, ?, ?, ?, ?, ?, 0, NULL, ?, NULL)
				""",
				provider,
				eventId,
				merchantTradeNo,
				status,
				amount,
				rawPayload,
				Instant.now().toString());
		return rows > 0;
	}

	void markProcessed(String provider, String eventId, String message) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				UPDATE payment_webhook_events
				SET processed = 1, message = ?, processed_at = ?
				WHERE provider = ? AND event_id = ?
				""", message, now, provider, eventId);
	}

	void markRejected(String provider, String eventId, String message) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				UPDATE payment_webhook_events
				SET processed = 0, message = ?, processed_at = ?
				WHERE provider = ? AND event_id = ?
				""", message, now, provider, eventId);
	}

	Optional<WebhookEventRow> findEvent(String provider, String eventId) {
		List<WebhookEventRow> rows = jdbcTemplate.query("""
				SELECT provider, event_id, merchant_trade_no, status, amount, processed, message
				FROM payment_webhook_events
				WHERE provider = ? AND event_id = ?
				""", (rs, rowNum) -> new WebhookEventRow(
				rs.getString("provider"),
				rs.getString("event_id"),
				rs.getString("merchant_trade_no"),
				rs.getString("status"),
				rs.getInt("amount"),
				rs.getInt("processed") == 1,
				rs.getString("message")), provider, eventId);
		return rows.stream().findFirst();
	}

	record WebhookEventRow(
			String provider,
			String eventId,
			String merchantTradeNo,
			String status,
			int amount,
			boolean processed,
			String message) {
	}
}
