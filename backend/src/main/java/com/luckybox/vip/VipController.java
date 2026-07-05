package com.luckybox.vip;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account/vip")
class VipController {

	private final VipService vipService;

	VipController(VipService vipService) {
		this.vipService = vipService;
	}

	@GetMapping
	VipStatusResponse status() {
		return vipService.status();
	}
}
