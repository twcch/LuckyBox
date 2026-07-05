package com.luckybox.leaderboard;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class LeaderboardService {

	private final LeaderboardRepository leaderboardRepository;
	private final Duration popularCampaignCacheTtl;
	private final Clock clock;
	private final ConcurrentMap<PopularCampaignCacheKey, CachedPopularCampaigns> popularCampaignCache =
			new ConcurrentHashMap<>();

	@Autowired
	LeaderboardService(
			LeaderboardRepository leaderboardRepository,
			@Value("${luckybox.leaderboard.popular-cache-ttl-seconds:15}") int popularCampaignCacheTtlSeconds) {
		this(leaderboardRepository, popularCampaignCacheTtlSeconds, Clock.systemUTC());
	}

	LeaderboardService(LeaderboardRepository leaderboardRepository, int popularCampaignCacheTtlSeconds, Clock clock) {
		this.leaderboardRepository = leaderboardRepository;
		this.popularCampaignCacheTtl = Duration.ofSeconds(Math.max(0, popularCampaignCacheTtlSeconds));
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	LeaderboardResponse leaderboard(int liveLimit, int popularLimit, int luckyLimit) {
		int normalizedLiveLimit = Math.clamp(liveLimit, 1, 20);
		int normalizedPopularLimit = Math.clamp(popularLimit, 1, 20);
		int normalizedLuckyLimit = Math.clamp(luckyLimit, 1, 20);
		return new LeaderboardResponse(
				leaderboardRepository.latestDraws(normalizedLiveLimit),
				popularCampaigns(normalizedPopularLimit),
				leaderboardRepository.luckyMembers(normalizedLuckyLimit),
				Instant.now().toString());
	}

	@Transactional(readOnly = true)
	CampaignDrawHistoryResponse campaignDraws(String slug, int limit) {
		int normalizedLimit = Math.clamp(limit, 1, 20);
		return new CampaignDrawHistoryResponse(
				leaderboardRepository.latestDrawsForCampaign(slug, normalizedLimit),
				Instant.now().toString());
	}

	private List<LeaderboardPopularCampaignResponse> popularCampaigns(int limit) {
		if (popularCampaignCacheTtl.isZero()) {
			return leaderboardRepository.popularCampaigns(limit);
		}
		Instant now = clock.instant();
		PopularCampaignCacheKey cacheKey = new PopularCampaignCacheKey(limit);
		CachedPopularCampaigns cached = popularCampaignCache.get(cacheKey);
		if (cached != null && cached.expiresAt().isAfter(now)) {
			return cached.campaigns();
		}
		List<LeaderboardPopularCampaignResponse> freshCampaigns =
				List.copyOf(leaderboardRepository.popularCampaigns(limit));
		popularCampaignCache.put(cacheKey, new CachedPopularCampaigns(freshCampaigns, now.plus(popularCampaignCacheTtl)));
		return freshCampaigns;
	}

	private record PopularCampaignCacheKey(int limit) {
	}

	private record CachedPopularCampaigns(List<LeaderboardPopularCampaignResponse> campaigns, Instant expiresAt) {
	}
}
