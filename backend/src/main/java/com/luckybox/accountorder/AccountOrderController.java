package com.luckybox.accountorder;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account/orders")
class AccountOrderController {

	private final AccountOrderService accountOrderService;

	AccountOrderController(AccountOrderService accountOrderService) {
		this.accountOrderService = accountOrderService;
	}

	@GetMapping
	AccountOrdersResponse orders() {
		return accountOrderService.orders();
	}
}
