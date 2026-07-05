package com.luckybox.admin.dashboard;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
class AdminDashboardController {

	private final AdminDashboardService adminDashboardService;

	AdminDashboardController(AdminDashboardService adminDashboardService) {
		this.adminDashboardService = adminDashboardService;
	}

	@GetMapping
	AdminDashboardResponse dashboard() {
		return adminDashboardService.dashboard();
	}
}
