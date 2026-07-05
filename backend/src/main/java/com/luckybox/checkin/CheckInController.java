package com.luckybox.checkin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account/check-in")
class CheckInController {

	private final CheckInService checkInService;

	CheckInController(CheckInService checkInService) {
		this.checkInService = checkInService;
	}

	@GetMapping
	CheckInStatusResponse status() {
		return checkInService.status();
	}

	@PostMapping
	CheckInResultResponse checkIn() {
		return checkInService.checkIn();
	}
}
