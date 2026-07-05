package com.luckybox.admin.campaign;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/prizes")
class AdminPrizeLibraryController {

	private final AdminPrizeLibraryService adminPrizeLibraryService;

	AdminPrizeLibraryController(AdminPrizeLibraryService adminPrizeLibraryService) {
		this.adminPrizeLibraryService = adminPrizeLibraryService;
	}

	@GetMapping
	List<AdminPrizeLibraryResponse> prizes(
			@RequestParam(required = false) String campaignStatus,
			@RequestParam(required = false) String rank,
			@RequestParam(required = false) Boolean lastPrize,
			@RequestParam(required = false, name = "q") String keyword,
			@RequestParam(defaultValue = "200") int limit) {
		return adminPrizeLibraryService.prizes(campaignStatus, rank, lastPrize, keyword, limit);
	}
}
