package com.luckybox.admin.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PaymentReconciliationScriptTests {

	@TempDir
	Path tempDir;

	@Test
	void strictModePassesWhenPaymentWebhookAndLedgerMatch() throws Exception {
		assumeTrue(sqlite3Available(), "sqlite3 command is required for the reconciliation script");
		Path database = tempDir.resolve("clean.sqlite");
		createSchema(database);
		try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
			execute(connection, """
					INSERT INTO payment_orders (
						id, user_id, provider, merchant_trade_no, amount, point_amount, bonus_point_amount,
						status, provider_payload, paid_at, created_at, updated_at
					)
					VALUES (1, 10, 'MOCK', 'MOCK-10-clean', 500, 500, 50, 'PAID', '{}',
						'2026-06-30T00:00:00Z', '2026-06-30T00:00:00Z', '2026-06-30T00:00:00Z')
					""");
			execute(connection, """
					INSERT INTO wallet_ledger (
						user_id, wallet_id, type, amount, point_kind, balance_after,
						reference_type, reference_id, reason, created_by, created_at
					)
					VALUES
						(10, 10, 'TOP_UP', 500, 'CASH', 500, 'PaymentOrder', 1, 'Mock 儲值入點', 10, '2026-06-30T00:00:00Z'),
						(10, 10, 'TOP_UP_BONUS', 50, 'BONUS', 50, 'PaymentOrder', 1, 'Mock 儲值贈點', 10, '2026-06-30T00:00:00Z')
					""");
			execute(connection, """
					INSERT INTO payment_webhook_events (
						provider, event_id, merchant_trade_no, status, amount,
						raw_payload, processed, message, created_at, processed_at
					)
					VALUES ('MOCK', 'evt-clean', 'MOCK-10-clean', 'PAID', 500, '{}', 1, 'OK',
						'2026-06-30T00:00:00Z', '2026-06-30T00:00:00Z')
					""");
		}

		ProcessResult result = runScript(database);

		assertThat(result.exitCode()).isZero();
		assertThat(result.output()).contains("No reconciliation issues found.");
		assertThat(result.output()).contains("Issue count: 0");
	}

	@Test
	void strictModeFailsWhenLedgerOrWebhookAmountsDoNotMatch() throws Exception {
		assumeTrue(sqlite3Available(), "sqlite3 command is required for the reconciliation script");
		Path database = tempDir.resolve("dirty.sqlite");
		createSchema(database);
		try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
			execute(connection, """
					INSERT INTO payment_orders (
						id, user_id, provider, merchant_trade_no, amount, point_amount, bonus_point_amount,
						status, provider_payload, paid_at, created_at, updated_at
					)
					VALUES (1, 10, 'MOCK', 'MOCK-10-dirty', 500, 500, 50, 'PAID', '{}',
						'2026-06-30T00:00:00Z', '2026-06-30T00:00:00Z', '2026-06-30T00:00:00Z')
					""");
			execute(connection, """
					INSERT INTO wallet_ledger (
						user_id, wallet_id, type, amount, point_kind, balance_after,
						reference_type, reference_id, reason, created_by, created_at
					)
					VALUES (10, 10, 'TOP_UP', 400, 'CASH', 400, 'PaymentOrder', 1, 'Mock 儲值入點', 10,
						'2026-06-30T00:00:00Z')
					""");
			execute(connection, """
					INSERT INTO payment_webhook_events (
						provider, event_id, merchant_trade_no, status, amount,
						raw_payload, processed, message, created_at, processed_at
					)
					VALUES ('MOCK', 'evt-dirty', 'MOCK-10-dirty', 'PAID', 499, '{}', 0, 'AMOUNT_MISMATCH',
						'2026-06-30T00:00:00Z', '2026-06-30T00:00:00Z')
					""");
		}

		ProcessResult result = runScript(database);

		assertThat(result.exitCode()).isEqualTo(2);
		assertThat(result.output()).contains("PAID_LEDGER_MISMATCH");
		assertThat(result.output()).contains("WEBHOOK_AMOUNT_MISMATCH");
	}

	@Test
	void providerCsvReconciliationPassesWhenExportMatchesLocalOrders() throws Exception {
		assumeTrue(python3Available(), "python3 command is required for the provider reconciliation script");
		Path database = tempDir.resolve("provider-clean.sqlite");
		Path csv = tempDir.resolve("ecpay-clean.csv");
		createSchema(database);
		try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
			execute(connection, """
					INSERT INTO payment_orders (
						id, user_id, provider, merchant_trade_no, amount, point_amount, bonus_point_amount,
						status, provider_payload, paid_at, created_at, updated_at
					)
					VALUES
						(1, 10, 'ECPAY', 'LBM0CLEAN', 500, 500, 50, 'PAID', '{}',
							'2026-07-05T00:00:00Z', '2026-07-05T00:00:00Z', '2026-07-05T00:00:00Z'),
						(2, 10, 'ECPAY', 'LBM0CANCEL', 300, 300, 30, 'CANCELED', '{}',
							NULL, '2026-07-05T00:00:00Z', '2026-07-05T00:00:00Z')
					""");
		}
		Files.writeString(csv, """
				MerchantTradeNo,TotalAmount,RtnCode,TradeNo
				LBM0CLEAN,500,1,20001
				LBM0CANCEL,300,CANCELED,20002
				""");

		ProcessResult result = runProviderScript(database, csv);

		assertThat(result.exitCode()).isZero();
		assertThat(result.output()).contains("No provider reconciliation issues found.");
		assertThat(result.output()).contains("Issue count: 0");
	}

	@Test
	void providerCsvReconciliationFailsForMissingDuplicateAndMismatchedRows() throws Exception {
		assumeTrue(python3Available(), "python3 command is required for the provider reconciliation script");
		Path database = tempDir.resolve("provider-dirty.sqlite");
		Path csv = tempDir.resolve("ecpay-dirty.csv");
		createSchema(database);
		try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
			execute(connection, """
					INSERT INTO payment_orders (
						id, user_id, provider, merchant_trade_no, amount, point_amount, bonus_point_amount,
						status, provider_payload, paid_at, created_at, updated_at
					)
					VALUES
						(1, 10, 'ECPAY', 'LBM0DIRTY', 500, 500, 50, 'PAID', '{}',
							'2026-07-05T00:00:00Z', '2026-07-05T00:00:00Z', '2026-07-05T00:00:00Z'),
						(2, 10, 'ECPAY', 'LBM0MISSING', 300, 300, 30, 'PAID', '{}',
							'2026-07-05T00:00:00Z', '2026-07-05T00:00:00Z', '2026-07-05T00:00:00Z')
					""");
		}
		Files.writeString(csv, """
				MerchantTradeNo,TotalAmount,RtnCode,TradeNo
				LBM0DIRTY,499,1,20001
				LBM0DIRTY,499,1,20001-DUP
				LBM0EXTRA,200,1,20002
				""");

		ProcessResult result = runProviderScript(database, csv);

		assertThat(result.exitCode()).isEqualTo(2);
		assertThat(result.output()).contains("PROVIDER_AMOUNT_MISMATCH");
		assertThat(result.output()).contains("PROVIDER_DUPLICATE_ROW");
		assertThat(result.output()).contains("PROVIDER_ORDER_MISSING_IN_FILE");
		assertThat(result.output()).contains("PROVIDER_ROW_ORDER_NOT_FOUND");
	}

	private static void createSchema(Path database) throws Exception {
		Files.createFile(database);
		try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
			execute(connection, """
					CREATE TABLE payment_orders (
						id INTEGER PRIMARY KEY,
						user_id INTEGER NOT NULL,
						provider TEXT NOT NULL,
						merchant_trade_no TEXT NOT NULL,
						amount INTEGER NOT NULL,
						point_amount INTEGER NOT NULL,
						bonus_point_amount INTEGER NOT NULL,
						status TEXT NOT NULL,
						provider_payload TEXT,
						paid_at TEXT,
						created_at TEXT NOT NULL,
						updated_at TEXT NOT NULL
					)
					""");
			execute(connection, """
					CREATE TABLE payment_webhook_events (
						id INTEGER PRIMARY KEY AUTOINCREMENT,
						provider TEXT NOT NULL,
						event_id TEXT NOT NULL,
						merchant_trade_no TEXT NOT NULL,
						status TEXT NOT NULL,
						amount INTEGER NOT NULL,
						raw_payload TEXT NOT NULL,
						processed INTEGER NOT NULL,
						message TEXT,
						created_at TEXT NOT NULL,
						processed_at TEXT
					)
					""");
			execute(connection, """
					CREATE TABLE wallet_ledger (
						id INTEGER PRIMARY KEY AUTOINCREMENT,
						user_id INTEGER NOT NULL,
						wallet_id INTEGER NOT NULL,
						type TEXT NOT NULL,
						amount INTEGER NOT NULL,
						point_kind TEXT NOT NULL,
						balance_after INTEGER NOT NULL,
						reference_type TEXT,
						reference_id INTEGER,
						reason TEXT,
						created_by INTEGER,
						created_at TEXT NOT NULL
					)
					""");
		}
	}

	private ProcessResult runScript(Path database) throws Exception {
		Process process = new ProcessBuilder(
				"bash",
				"scripts/reconcile-payments.sh",
				"--strict",
				"--db",
				database.toString())
				.redirectErrorStream(true)
				.start();
		boolean finished = process.waitFor(10, TimeUnit.SECONDS);
		assertThat(finished).isTrue();
		return new ProcessResult(process.exitValue(), new String(process.getInputStream().readAllBytes()));
	}

	private ProcessResult runProviderScript(Path database, Path csv) throws Exception {
		Process process = new ProcessBuilder(
				"python3",
				"scripts/reconcile-provider-payments.py",
				"--strict",
				"--db",
				database.toString(),
				"--provider",
				"ECPAY",
				"--file",
				csv.toString(),
				"--merchant-trade-no-column",
				"MerchantTradeNo",
				"--amount-column",
				"TotalAmount",
				"--status-column",
				"RtnCode",
				"--event-id-column",
				"TradeNo")
				.redirectErrorStream(true)
				.start();
		boolean finished = process.waitFor(10, TimeUnit.SECONDS);
		assertThat(finished).isTrue();
		return new ProcessResult(process.exitValue(), new String(process.getInputStream().readAllBytes()));
	}

	private static boolean sqlite3Available() {
		try {
			Process process = new ProcessBuilder("sqlite3", "--version")
					.redirectErrorStream(true)
					.start();
			return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
		} catch (Exception exception) {
			return false;
		}
	}

	private static boolean python3Available() {
		try {
			Process process = new ProcessBuilder("python3", "--version")
					.redirectErrorStream(true)
					.start();
			return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
		} catch (Exception exception) {
			return false;
		}
	}

	private static void execute(Connection connection, String sql) throws Exception {
		try (Statement statement = connection.createStatement()) {
			statement.execute(sql);
		}
	}

	private record ProcessResult(int exitCode, String output) {
	}
}
