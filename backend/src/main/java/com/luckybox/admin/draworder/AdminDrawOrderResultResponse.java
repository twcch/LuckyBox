package com.luckybox.admin.draworder;

public record AdminDrawOrderResultResponse(
		long id,
		int resultIndex,
		String ticketSerialNumber,
		String prizeRank,
		String prizeName,
		boolean lastPrize,
		String randomProof,
		String createdAt) {
}
