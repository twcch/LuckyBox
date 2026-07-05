package com.luckybox.fairness;

import java.util.List;

public record FairnessResponse(
		String slug,
		String fairnessMode,
		String status,
		String seedHash,
		String revealedSeed,
		boolean revealed,
		int totalTickets,
		int drawnTickets,
		String algorithm,
		List<FairnessDrawResponse> draws) {
}
