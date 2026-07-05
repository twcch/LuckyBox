package com.luckybox.admin.settings;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/settings")
class AdminSettingsController {

	private final AdminSettingsService adminSettingsService;

	AdminSettingsController(AdminSettingsService adminSettingsService) {
		this.adminSettingsService = adminSettingsService;
	}

	@GetMapping
	AdminSettingsResponse settings() {
		return adminSettingsService.settings();
	}
}
