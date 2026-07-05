package com.luckybox.admin.campaign;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/campaigns/{campaignId}")
class AdminCampaignPrizeController {

	private final AdminCampaignPrizeService adminCampaignPrizeService;

	AdminCampaignPrizeController(AdminCampaignPrizeService adminCampaignPrizeService) {
		this.adminCampaignPrizeService = adminCampaignPrizeService;
	}

	@GetMapping("/prizes")
	AdminCampaignPrizeOverviewResponse prizes(@PathVariable long campaignId) {
		return adminCampaignPrizeService.prizes(campaignId);
	}

	@GetMapping("/tickets")
	java.util.List<AdminCampaignTicketResponse> tickets(
			@PathVariable long campaignId,
			@RequestParam(required = false) String status,
			@RequestParam(required = false, name = "q") String keyword,
			@RequestParam(defaultValue = "200") int limit) {
		return adminCampaignPrizeService.tickets(campaignId, status, keyword, limit);
	}

	@PostMapping("/prizes")
	@ResponseStatus(HttpStatus.CREATED)
	AdminCampaignPrizeResponse createPrize(
			@PathVariable long campaignId,
			@Valid @RequestBody AdminCampaignPrizeRequest request) {
		return adminCampaignPrizeService.createPrize(campaignId, request);
	}

	@PatchMapping("/prizes/{prizeId}")
	AdminCampaignPrizeResponse updatePrize(
			@PathVariable long campaignId,
			@PathVariable long prizeId,
			@Valid @RequestBody AdminCampaignPrizeRequest request) {
		return adminCampaignPrizeService.updatePrize(campaignId, prizeId, request);
	}

	@PostMapping("/tickets/generate")
	AdminTicketGenerationResponse generateTickets(@PathVariable long campaignId) {
		return adminCampaignPrizeService.generateTickets(campaignId);
	}
}
