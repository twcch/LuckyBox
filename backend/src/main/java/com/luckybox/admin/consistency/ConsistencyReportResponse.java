package com.luckybox.admin.consistency;

import java.util.List;

public record ConsistencyReportResponse(
		String generatedAt,
		int totalCampaigns,
		int findingCount,
		List<ConsistencyFinding> findings) {
}
