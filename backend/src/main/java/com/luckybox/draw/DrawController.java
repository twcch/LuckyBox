package com.luckybox.draw;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/account/draw-orders")
class DrawController {

	private final DrawService drawService;

	DrawController(DrawService drawService) {
		this.drawService = drawService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	DrawOrderResponse createDrawOrder(@Valid @RequestBody CreateDrawOrderRequest request) {
		return drawService.createDrawOrder(request);
	}

	@GetMapping("/{orderId}")
	DrawOrderResponse drawOrder(@PathVariable long orderId) {
		return drawService.findDrawOrder(orderId);
	}
}
