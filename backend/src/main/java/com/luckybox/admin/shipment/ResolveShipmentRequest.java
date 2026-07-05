package com.luckybox.admin.shipment;

import jakarta.validation.constraints.Size;

/** 出貨瑕疵/換貨處理輸入：resolution = RETURNED（退回戰利品盒）或 EXCHANGED（換貨），reason 必填。 */
public record ResolveShipmentRequest(
		@Size(max = 40, message = "處理方式不可超過 40 字")
		String resolution,

		@Size(max = 200, message = "處理原因不可超過 200 字")
		String reason) {
}
