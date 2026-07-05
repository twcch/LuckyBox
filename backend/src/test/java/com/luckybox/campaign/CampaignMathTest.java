package com.luckybox.campaign;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CampaignMathTest {

	@Test
	void probabilityRoundsToTwoDecimals() {
		assertThat(CampaignMath.probability(1, 80, false)).isEqualTo(1.25);
		assertThat(CampaignMath.probability(2, 3, false)).isEqualTo(66.67);
	}

	@Test
	void probabilityIsZeroWhenNoRegularTicketsOrPrizeIsLastPrize() {
		assertThat(CampaignMath.probability(1, 0, false)).isZero();
		assertThat(CampaignMath.probability(1, 80, true)).isZero();
	}

	@Test
	void remainingRateRoundsToOneDecimal() {
		assertThat(CampaignMath.remainingRate(80, 100)).isEqualTo(80.0);
		assertThat(CampaignMath.remainingRate(1, 6)).isEqualTo(16.7);
		assertThat(CampaignMath.remainingRate(5, 0)).isZero();
	}
}
