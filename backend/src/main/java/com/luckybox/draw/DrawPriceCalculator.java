package com.luckybox.draw;

final class DrawPriceCalculator {

	private DrawPriceCalculator() {
	}

	static int discountAmount(int couponValue, int originalPointSpent) {
		if (couponValue <= 0 || originalPointSpent <= 0) {
			return 0;
		}
		return Math.min(couponValue, originalPointSpent);
	}

	static int finalPointSpent(int originalPointSpent, int discountAmount) {
		return Math.max(originalPointSpent - Math.max(discountAmount, 0), 0);
	}
}
