package com.luckybox.wallet;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import jakarta.validation.Valid;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/webhooks/payment")
class PaymentWebhookController {

	private final PaymentWebhookService paymentWebhookService;
	private final EcpayPaymentService ecpayPaymentService;
	private final LinePayPaymentService linePayPaymentService;
	private final JkoPayPaymentService jkoPayPaymentService;

	PaymentWebhookController(
			PaymentWebhookService paymentWebhookService,
			EcpayPaymentService ecpayPaymentService,
			LinePayPaymentService linePayPaymentService,
			JkoPayPaymentService jkoPayPaymentService) {
		this.paymentWebhookService = paymentWebhookService;
		this.ecpayPaymentService = ecpayPaymentService;
		this.linePayPaymentService = linePayPaymentService;
		this.jkoPayPaymentService = jkoPayPaymentService;
	}

	@PostMapping(
			value = "/ecpay",
			consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
			produces = MediaType.TEXT_PLAIN_VALUE)
	String handleEcpay(@RequestParam MultiValueMap<String, String> formValues) {
		return ecpayPaymentService.handleCallback(formValues);
	}

	@GetMapping("/linepay/confirm/{merchantTradeNo}")
	RedirectView confirmLinePay(
			@PathVariable String merchantTradeNo,
			@RequestParam String transactionId) {
		return linePayPaymentService.confirm(merchantTradeNo, transactionId);
	}

	@GetMapping("/linepay/cancel/{merchantTradeNo}")
	RedirectView cancelLinePay(@PathVariable String merchantTradeNo) {
		return linePayPaymentService.cancel(merchantTradeNo);
	}

	@PostMapping("/jkopay/confirm")
	JkoPayConfirmResponse confirmJkoPay(@Valid @RequestBody JkoPayConfirmRequest request) {
		return jkoPayPaymentService.confirm(request);
	}

	@PostMapping("/jkopay/result")
	@ResponseStatus(HttpStatus.ACCEPTED)
	PaymentWebhookResponse handleJkoPayResult(@Valid @RequestBody JsonNode payload) {
		return jkoPayPaymentService.handleResult(payload);
	}

	@PostMapping("/{provider}")
	@ResponseStatus(HttpStatus.ACCEPTED)
	PaymentWebhookResponse handle(
			@PathVariable String provider,
			@RequestHeader(name = "X-LuckyBox-Signature", required = false) String signature,
			@Valid @RequestBody PaymentWebhookRequest request) {
		return paymentWebhookService.handle(provider, request, signature);
	}
}
