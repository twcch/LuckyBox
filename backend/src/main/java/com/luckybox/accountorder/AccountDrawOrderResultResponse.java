package com.luckybox.accountorder;

public record AccountDrawOrderResultResponse(
		long id,
		String ticketSerialNumber,
		String prizeRank,
		String prizeName,
		boolean lastPrize) {
}
