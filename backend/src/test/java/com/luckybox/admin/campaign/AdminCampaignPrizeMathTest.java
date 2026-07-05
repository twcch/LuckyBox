package com.luckybox.admin.campaign;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class AdminCampaignPrizeMathTest {

	@Test
	void totalAndRemainingQuantitiesExcludeLastPrize() {
		List<AdminCampaignPrizeResponse> prizes = List.of(
				prize(1, 5, 4, false),
				prize(2, 2, 1, false),
				prize(3, 1, 1, true));

		assertThat(AdminCampaignPrizeMath.totalRegularQuantity(prizes)).isEqualTo(7);
		assertThat(AdminCampaignPrizeMath.remainingRegularQuantity(prizes)).isEqualTo(5);
	}

	private static AdminCampaignPrizeResponse prize(long id, int originalQuantity, int remainingQuantity, boolean lastPrize) {
		return new AdminCampaignPrizeResponse(
				id,
				10,
				lastPrize ? "LAST" : "A",
				lastPrize ? "最後賞" : "普通賞",
				"測試獎項",
				null,
				originalQuantity,
				remainingQuantity,
				lastPrize ? 0 : originalQuantity,
				lastPrize ? 0 : remainingQuantity,
				(int) id,
				lastPrize,
				"2026-07-02T00:00:00Z",
				"2026-07-02T00:00:00Z");
	}
}
