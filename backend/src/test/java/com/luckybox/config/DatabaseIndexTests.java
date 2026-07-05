package com.luckybox.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class DatabaseIndexTests {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void hotPathIndexesExistWithExpectedColumns() {
		assertIndexColumns("kuji_campaigns", "idx_campaign_status", List.of("status"));
		assertIndexColumns("kuji_tickets", "idx_ticket_campaign_status", List.of("campaign_id", "status"));
		assertIndexColumns("draw_orders", "idx_draw_user", List.of("user_id"));
		assertIndexColumns("wallet_ledger", "idx_ledger_user", List.of("user_id"));
	}

	private void assertIndexColumns(String table, String indexName, List<String> expectedColumns) {
		List<String> indexNames = jdbcTemplate.queryForList("PRAGMA index_list(%s)".formatted(table)).stream()
				.map(row -> String.valueOf(row.get("name")))
				.toList();
		assertThat(indexNames).contains(indexName);

		List<String> columnNames = jdbcTemplate.queryForList("PRAGMA index_info(%s)".formatted(indexName)).stream()
				.sorted(java.util.Comparator.comparingInt(row -> ((Number) row.get("seqno")).intValue()))
				.map(DatabaseIndexTests::columnName)
				.toList();
		assertThat(columnNames).isEqualTo(expectedColumns);
	}

	private static String columnName(Map<String, Object> row) {
		return String.valueOf(row.get("name"));
	}
}
