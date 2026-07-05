package com.luckybox.draw;

public record DrawResultResponse(
		long id,
		String ticketSerialNumber,
		String prizeRank,
		String prizeName,
		boolean lastPrize) {
}
