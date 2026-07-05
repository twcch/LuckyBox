package com.luckybox.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

class LeaderboardServiceTest {

	@Test
	void leaderboardCachesPopularCampaignsWithinTtl() {
		MutableClock clock = new MutableClock(Instant.parse("2026-07-01T00:00:00Z"));
		FakeLeaderboardRepository repository = new FakeLeaderboardRepository();
		LeaderboardService service = new LeaderboardService(repository, 30, clock);

		LeaderboardResponse first = service.leaderboard(4, 5, 6);
		LeaderboardResponse second = service.leaderboard(4, 5, 6);

		assertThat(repository.latestDrawCalls).isEqualTo(2);
		assertThat(repository.luckyMemberCalls).isEqualTo(2);
		assertThat(repository.popularCampaignCalls).isEqualTo(1);
		assertThat(second.popularCampaigns()).isEqualTo(first.popularCampaigns());
		assertThat(second.popularCampaigns().get(0).title()).isEqualTo("熱門賞池 1");
	}

	@Test
	void leaderboardRefreshesPopularCampaignsAfterTtl() {
		MutableClock clock = new MutableClock(Instant.parse("2026-07-01T00:00:00Z"));
		FakeLeaderboardRepository repository = new FakeLeaderboardRepository();
		LeaderboardService service = new LeaderboardService(repository, 5, clock);

		LeaderboardResponse first = service.leaderboard(4, 5, 6);
		clock.advance(Duration.ofSeconds(5));
		LeaderboardResponse second = service.leaderboard(4, 5, 6);

		assertThat(repository.popularCampaignCalls).isEqualTo(2);
		assertThat(first.popularCampaigns().get(0).title()).isEqualTo("熱門賞池 1");
		assertThat(second.popularCampaigns().get(0).title()).isEqualTo("熱門賞池 2");
	}

	@Test
	void leaderboardKeepsPopularCampaignCacheSeparatedByLimit() {
		MutableClock clock = new MutableClock(Instant.parse("2026-07-01T00:00:00Z"));
		FakeLeaderboardRepository repository = new FakeLeaderboardRepository();
		LeaderboardService service = new LeaderboardService(repository, 30, clock);

		LeaderboardResponse limitFive = service.leaderboard(4, 5, 6);
		LeaderboardResponse limitSeven = service.leaderboard(4, 7, 6);

		assertThat(repository.popularCampaignCalls).isEqualTo(2);
		assertThat(limitFive.popularCampaigns().get(0).slug()).isEqualTo("popular-1-limit-5");
		assertThat(limitSeven.popularCampaigns().get(0).slug()).isEqualTo("popular-2-limit-7");
	}

	@Test
	void leaderboardCanDisablePopularCampaignCache() {
		MutableClock clock = new MutableClock(Instant.parse("2026-07-01T00:00:00Z"));
		FakeLeaderboardRepository repository = new FakeLeaderboardRepository();
		LeaderboardService service = new LeaderboardService(repository, 0, clock);

		LeaderboardResponse first = service.leaderboard(4, 5, 6);
		LeaderboardResponse second = service.leaderboard(4, 5, 6);

		assertThat(repository.popularCampaignCalls).isEqualTo(2);
		assertThat(first.popularCampaigns().get(0).title()).isEqualTo("熱門賞池 1");
		assertThat(second.popularCampaigns().get(0).title()).isEqualTo("熱門賞池 2");
	}

	private static class FakeLeaderboardRepository extends LeaderboardRepository {

		private int latestDrawCalls;
		private int popularCampaignCalls;
		private int luckyMemberCalls;

		FakeLeaderboardRepository() {
			super(null);
		}

		@Override
		List<LeaderboardLiveDrawResponse> latestDraws(int limit) {
			latestDrawCalls++;
			return List.of(new LeaderboardLiveDrawResponse(
					latestDrawCalls,
					10 + latestDrawCalls,
					"Lu**",
					"campaign-%d".formatted(latestDrawCalls),
					"測試賞池",
					"A",
					"測試獎項",
					1,
					"2026-07-01T00:00:00Z"));
		}

		@Override
		List<LeaderboardPopularCampaignResponse> popularCampaigns(int limit) {
			popularCampaignCalls++;
			return List.of(new LeaderboardPopularCampaignResponse(
					popularCampaignCalls,
					"popular-%d-limit-%d".formatted(popularCampaignCalls, limit),
					"熱門賞池 %d".formatted(popularCampaignCalls),
					"LIVE",
					"開抽中",
					100,
					100,
					80 - popularCampaignCalls,
					20 + popularCampaignCalls,
					20.0 + popularCampaignCalls,
					20 + popularCampaignCalls,
					3,
					"A賞剩 1"));
		}

		@Override
		List<LeaderboardLuckyMemberResponse> luckyMembers(int limit) {
			luckyMemberCalls++;
			return List.of(new LeaderboardLuckyMemberResponse(1, "Lu**", luckyMemberCalls, 0, 0));
		}
	}

	private static class MutableClock extends Clock {

		private Instant instant;

		MutableClock(Instant instant) {
			this.instant = instant;
		}

		void advance(Duration duration) {
			instant = instant.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return instant;
		}
	}
}
