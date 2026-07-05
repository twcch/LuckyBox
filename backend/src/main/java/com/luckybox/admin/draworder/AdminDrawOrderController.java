package com.luckybox.admin.draworder;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/draw-orders")
class AdminDrawOrderController {

	private final AdminDrawOrderService adminDrawOrderService;

	AdminDrawOrderController(AdminDrawOrderService adminDrawOrderService) {
		this.adminDrawOrderService = adminDrawOrderService;
	}

	@GetMapping
	List<AdminDrawOrderResponse> drawOrders(
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String campaignSlug,
			@RequestParam(required = false, name = "q") String keyword) {
		return adminDrawOrderService.drawOrders(status, campaignSlug, keyword);
	}

	@GetMapping("/{orderId}")
	AdminDrawOrderDetailResponse drawOrder(@PathVariable long orderId) {
		return adminDrawOrderService.drawOrder(orderId);
	}
}
