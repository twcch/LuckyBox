package com.luckybox.wallet;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

public record JkoPayConfirmRequest(
		@JsonProperty("platform_order_id")
		@NotBlank
		String platformOrderId) {
}
