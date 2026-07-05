package com.luckybox.admin.shipment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateShipmentRequest(
		@NotBlank(message = "請選擇物流狀態")
		String status,

		@Size(max = 80, message = "物流商名稱不可超過 80 字")
		String carrier,

		@Size(max = 120, message = "追蹤碼不可超過 120 字")
		String trackingNumber,

		@Size(max = 500, message = "後台備註不可超過 500 字")
		String adminNote) {
}
