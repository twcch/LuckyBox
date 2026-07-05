package com.luckybox.campaign;

final class CampaignMath {

	private CampaignMath() {
	}

	static double remainingRate(int remainingTickets, int totalTickets) {
		if (totalTickets == 0) {
			return 0;
		}
		return Math.round((remainingTickets * 1000.0 / totalTickets)) / 10.0;
	}

	static double probability(int remainingQuantity, int remainingTickets, boolean lastPrize) {
		if (lastPrize || remainingTickets == 0) {
			return 0;
		}
		return Math.round((remainingQuantity * 10000.0 / remainingTickets)) / 100.0;
	}
}
