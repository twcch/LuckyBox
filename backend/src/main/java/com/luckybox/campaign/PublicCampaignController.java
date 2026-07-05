package com.luckybox.campaign;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/campaigns")
class PublicCampaignController {

	private final CampaignService campaignService;

	PublicCampaignController(CampaignService campaignService) {
		this.campaignService = campaignService;
	}

	@GetMapping
	CampaignPage listCampaigns(
			@RequestParam(name = "q", required = false) String keyword,
			@RequestParam(required = false) String sourceType,
			@RequestParam(required = false) String status,
			@RequestParam(defaultValue = "default") String sort,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "6") int size) {
		return campaignService.listVisibleCampaigns(new CampaignQuery(keyword, sourceType, status, sort, page, size));
	}

	@GetMapping("/{slug}")
	CampaignDetail getCampaign(@PathVariable String slug) {
		return campaignService.getCampaign(slug);
	}

	@GetMapping("/{slug}/probabilities")
	CampaignProbabilitiesResponse probabilities(@PathVariable String slug) {
		CampaignDetail campaign = campaignService.getCampaign(slug);
		return new CampaignProbabilitiesResponse(
				campaign.slug(),
				campaign.totalTickets(),
				campaign.remainingTickets(),
				campaign.prizes());
	}
}
