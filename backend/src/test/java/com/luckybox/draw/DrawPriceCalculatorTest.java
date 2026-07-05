package com.luckybox.draw;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DrawPriceCalculatorTest {

	@Test
	void couponDiscountIsCappedAtOriginalSpend() {
		assertThat(DrawPriceCalculator.discountAmount(150, 200)).isEqualTo(150);
		assertThat(DrawPriceCalculator.discountAmount(300, 200)).isEqualTo(200);
	}

	@Test
	void finalSpendNeverDropsBelowZero() {
		assertThat(DrawPriceCalculator.finalPointSpent(200, 150)).isEqualTo(50);
		assertThat(DrawPriceCalculator.finalPointSpent(200, 300)).isZero();
		assertThat(DrawPriceCalculator.finalPointSpent(200, -10)).isEqualTo(200);
	}
}
