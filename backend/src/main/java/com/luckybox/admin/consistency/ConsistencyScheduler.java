package com.luckybox.admin.consistency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Daily data-consistency check. Runs the same scan as {@code GET /api/admin/consistency} and records
 * every finding to the audit log so operators have a durable trail of detected anomalies. Cron is
 * configurable via {@code luckybox.consistency.cron} (default 03:00 Asia/Taipei).
 */
@Configuration
@EnableScheduling
class ConsistencyScheduler {

	private static final Logger log = LoggerFactory.getLogger(ConsistencyScheduler.class);

	private final ConsistencyService consistencyService;

	ConsistencyScheduler(ConsistencyService consistencyService) {
		this.consistencyService = consistencyService;
	}

	@Scheduled(cron = "${luckybox.consistency.cron:0 0 3 * * *}", zone = "Asia/Taipei")
	void runDailyConsistencyCheck() {
		int findings = consistencyService.auditScan();
		log.info("Daily consistency check complete: {} inconsistencies recorded", findings);
	}
}
