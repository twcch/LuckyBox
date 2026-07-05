package com.luckybox.leaderboard;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leaderboard")
class PublicLeaderboardController {

	private final LeaderboardService leaderboardService;

	PublicLeaderboardController(LeaderboardService leaderboardService) {
		this.leaderboardService = leaderboardService;
	}

	@GetMapping
	LeaderboardResponse leaderboard(
			@RequestParam(defaultValue = "12") int liveLimit,
			@RequestParam(defaultValue = "8") int popularLimit,
			@RequestParam(defaultValue = "10") int luckyLimit) {
		return leaderboardService.leaderboard(liveLimit, popularLimit, luckyLimit);
	}

	@GetMapping("/campaigns/{slug}/draws")
	CampaignDrawHistoryResponse campaignDraws(
			@PathVariable String slug,
			@RequestParam(defaultValue = "8") int limit) {
		return leaderboardService.campaignDraws(slug, limit);
	}
}
