package com.luckybox.account;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/2fa")
class Admin2faController {

	private final Admin2faService admin2faService;

	Admin2faController(Admin2faService admin2faService) {
		this.admin2faService = admin2faService;
	}

	@GetMapping
	TwoFactorStatusResponse status() {
		return admin2faService.status();
	}

	@PostMapping("/setup")
	TwoFactorSetupResponse setup() {
		return admin2faService.setup();
	}

	@PostMapping("/enable")
	TwoFactorStatusResponse enable(@Valid @RequestBody TwoFactorCodeRequest request) {
		return admin2faService.enable(request);
	}

	@PostMapping("/disable")
	TwoFactorStatusResponse disable(@Valid @RequestBody TwoFactorCodeRequest request) {
		return admin2faService.disable(request);
	}
}
