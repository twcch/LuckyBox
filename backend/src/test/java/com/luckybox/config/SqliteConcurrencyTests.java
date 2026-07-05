package com.luckybox.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class SqliteConcurrencyTests {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Test
	void concurrentAuditWritesAreSerializedWithoutLoss() throws Exception {
		String runId = UUID.randomUUID().toString();
		int taskCount = 8;
		ExecutorService executorService = Executors.newFixedThreadPool(taskCount);
		CountDownLatch start = new CountDownLatch(1);
		TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

		try {
			List<Future<?>> futures = new ArrayList<>();
			for (int index = 0; index < taskCount; index++) {
				int taskIndex = index;
				futures.add(executorService.submit(() -> {
					start.await();
					transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update("""
							INSERT INTO audit_logs (
								actor_id, actor_role, action, entity_type, entity_id, before_state, after_state, ip_address, created_at
							)
							VALUES (NULL, 'SYSTEM', ?, 'ConcurrencyProbe', ?, NULL, '{}', NULL, ?)
							""", "CONCURRENCY_TEST", runId + "-" + taskIndex, Instant.now().toString()));
					return null;
				}));
			}

			start.countDown();
			for (Future<?> future : futures) {
				future.get();
			}
		} finally {
			executorService.shutdownNow();
		}

		Integer inserted = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM audit_logs WHERE action = 'CONCURRENCY_TEST' AND entity_id LIKE ?
				""", Integer.class, runId + "-%");

		assertThat(inserted).isEqualTo(taskCount);
	}
}
