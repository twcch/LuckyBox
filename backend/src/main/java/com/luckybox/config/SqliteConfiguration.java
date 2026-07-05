package com.luckybox.config;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
class SqliteConfiguration {

	private static final Logger log = LoggerFactory.getLogger(SqliteConfiguration.class);

	@Bean
	ApplicationRunner sqlitePragmaRunner(DataSource dataSource) {
		return args -> {
			JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
			String journalMode = jdbcTemplate.queryForObject("PRAGMA journal_mode=WAL", String.class);
			jdbcTemplate.execute("PRAGMA busy_timeout=5000");
			jdbcTemplate.execute("PRAGMA foreign_keys=ON");
			log.info("SQLite initialized with journal_mode={} and busy_timeout=5000ms", journalMode);
		};
	}
}
