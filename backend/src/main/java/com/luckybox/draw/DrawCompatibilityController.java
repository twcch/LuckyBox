package com.luckybox.draw;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class DrawCompatibilityController {

	private final DrawService drawService;

	DrawCompatibilityController(DrawService drawService) {
		this.drawService = drawService;
	}

	@GetMapping("/draw-orders/{orderId}")
	DrawOrderResponse drawOrder(@PathVariable long orderId) {
		return drawService.findDrawOrder(orderId);
	}

	@GetMapping("/draw-results/{resultId}")
	DrawResultResponse drawResult(@PathVariable long resultId) {
		return drawService.findDrawResult(resultId);
	}
}
