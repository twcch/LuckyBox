package com.luckybox.prizebox;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/account")
class PrizeBoxController {

	private final PrizeBoxService prizeBoxService;

	PrizeBoxController(PrizeBoxService prizeBoxService) {
		this.prizeBoxService = prizeBoxService;
	}

	@GetMapping("/prizes")
	PrizeBoxOverviewResponse prizes(
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String campaignSlug) {
		return prizeBoxService.prizes(status, campaignSlug);
	}

	@GetMapping("/shipments")
	List<ShipmentResponse> shipments() {
		return prizeBoxService.shipments();
	}

	@GetMapping("/shipments/{shipmentId}")
	ShipmentResponse shipment(@PathVariable long shipmentId) {
		return prizeBoxService.shipment(shipmentId);
	}

	@PostMapping("/shipments")
	@ResponseStatus(HttpStatus.CREATED)
	ShipmentResponse createShipment(@Valid @RequestBody CreateShipmentRequest request) {
		return prizeBoxService.createShipment(request);
	}
}
