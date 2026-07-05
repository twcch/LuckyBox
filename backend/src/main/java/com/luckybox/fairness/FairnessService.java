package com.luckybox.fairness;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.luckybox.common.ApiException;

@Service
class FairnessService {

	private static final String COMMIT_REVEAL_ALGORITHM =
			"發布時以 SecureRandom 產生 server seed，公開承諾 seed_hash = SHA-256(server_seed)。"
					+ "第 n 抽以 HMAC-SHA256(server_seed, \"{drawOrderId}:{index}\") 對當下可抽籤數取餘數選出 ticket，"
					+ "並將該 HMAC 記錄為 random_proof。完抽後公開 revealed_seed，"
					+ "任何人可直接驗證 SHA-256(revealed_seed) == seed_hash（種子未被更換），"
					+ "並對每一抽重算 HMAC-SHA256(revealed_seed, nonce) 是否等於其 random_proof（結果於開抽前已被承諾、無法事後竄改）。"
					+ "若要進一步重現「選到哪一張 ticket」，需依抽賞順序回放 offset = HMAC mod 當下可抽籤數。";

	private static final String SERVER_RANDOM_ALGORITHM =
			"伺服器端隨機選票（SERVER_RANDOM）。random_proof 僅記錄 ticket serial，"
					+ "未提供 seed 承諾與揭露驗證；如需可驗證公平性請改用 HASH_COMMIT_REVEAL 模式。";

	private final FairnessRepository fairnessRepository;

	FairnessService(FairnessRepository fairnessRepository) {
		this.fairnessRepository = fairnessRepository;
	}

	FairnessResponse getFairness(String slug) {
		FairnessRepository.FairnessCampaign campaign = fairnessRepository.findCampaign(slug);
		if (campaign == null) {
			throw new ApiException(HttpStatus.NOT_FOUND, "CAMPAIGN_NOT_FOUND", "找不到指定賞池。");
		}
		List<FairnessDrawResponse> draws = fairnessRepository.findDraws(campaign.id());
		// Count actual draw_results so the summary agrees with the draws list. A sold-out last-prize
		// campaign has one synthesized last-prize draw that lives outside total_tickets, so
		// total_tickets - remaining_tickets would under-count it by one.
		int drawnTickets = draws.size();
		String algorithm = "HASH_COMMIT_REVEAL".equals(campaign.fairnessMode())
				? COMMIT_REVEAL_ALGORITHM
				: SERVER_RANDOM_ALGORITHM;
		return new FairnessResponse(
				campaign.slug(),
				campaign.fairnessMode(),
				campaign.status(),
				campaign.seedHash(),
				campaign.revealedSeed(),
				campaign.revealedSeed() != null,
				campaign.totalTickets(),
				drawnTickets,
				algorithm,
				draws);
	}
}
