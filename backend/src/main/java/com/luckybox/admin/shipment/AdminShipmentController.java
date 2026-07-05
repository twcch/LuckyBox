package com.luckybox.admin.shipment;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/shipments")
class AdminShipmentController {

	private final AdminShipmentService adminShipmentService;

	AdminShipmentController(AdminShipmentService adminShipmentService) {
		this.adminShipmentService = adminShipmentService;
	}

	@GetMapping
	List<AdminShipmentResponse> shipments(@RequestParam(required = false) String status) {
		return adminShipmentService.shipments(status);
	}

	@PatchMapping("/{shipmentId}")
	AdminShipmentResponse updateShipment(
			@PathVariable long shipmentId,
			@Valid @RequestBody UpdateShipmentRequest request) {
		return adminShipmentService.updateShipment(shipmentId, request);
	}

	@PostMapping("/{shipmentId}/resolve")
	AdminShipmentResponse resolveShipment(
			@PathVariable long shipmentId,
			@Valid @RequestBody ResolveShipmentRequest request) {
		return adminShipmentService.resolveShipment(shipmentId, request);
	}
}
