package com.luckybox.admin.campaign;

import java.util.List;

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
@RequestMapping("/api/admin/campaigns")
class AdminCampaignController {

	private final AdminCampaignService adminCampaignService;

	AdminCampaignController(AdminCampaignService adminCampaignService) {
		this.adminCampaignService = adminCampaignService;
	}

	@GetMapping
	List<AdminCampaignResponse> campaigns(
			@RequestParam(required = false) String status,
			@RequestParam(required = false, name = "q") String keyword,
			@RequestParam(defaultValue = "latest") String sort) {
		return adminCampaignService.campaigns(status, keyword, sort);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	AdminCampaignResponse createCampaign(@Valid @RequestBody AdminCampaignRequest request) {
		return adminCampaignService.createCampaign(request);
	}

	@PatchMapping("/{campaignId}")
	AdminCampaignResponse updateCampaign(
			@PathVariable long campaignId,
			@Valid @RequestBody AdminCampaignRequest request) {
		return adminCampaignService.updateCampaign(campaignId, request);
	}

	@PostMapping("/{campaignId}/publish")
	AdminCampaignResponse publishCampaign(@PathVariable long campaignId) {
		return adminCampaignService.publishCampaign(campaignId);
	}

	@PostMapping("/{campaignId}/pause")
	AdminCampaignResponse pauseCampaign(@PathVariable long campaignId) {
		return adminCampaignService.pauseCampaign(campaignId);
	}

	@PostMapping("/{campaignId}/correction-version")
	@ResponseStatus(HttpStatus.CREATED)
	AdminCampaignResponse createCorrectionVersion(@PathVariable long campaignId) {
		return adminCampaignService.createCorrectionVersion(campaignId);
	}

	@PostMapping("/{campaignId}/dry-run")
	AdminCampaignDryRunResponse dryRunCampaign(@PathVariable long campaignId) {
		return adminCampaignService.dryRunCampaign(campaignId);
	}
}
