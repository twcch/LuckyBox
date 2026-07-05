package com.luckybox.fairness;

public record FairnessDrawResponse(
		int sequence,
		String ticketSerial,
		String prizeRank,
		String prizeName,
		String randomProof) {
}
