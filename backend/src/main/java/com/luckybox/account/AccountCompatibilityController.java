package com.luckybox.account;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
class AccountCompatibilityController {

	private final AccountService accountService;

	AccountCompatibilityController(AccountService accountService) {
		this.accountService = accountService;
	}

	@GetMapping("/me")
	AuthUser me() {
		return accountService.currentUser();
	}

	@GetMapping("/addresses")
	List<AddressResponse> addresses() {
		return accountService.addresses();
	}

	@PostMapping("/addresses")
	@ResponseStatus(HttpStatus.CREATED)
	AddressResponse createAddress(@Valid @RequestBody AddressRequest request) {
		return accountService.createAddress(request);
	}

	@PatchMapping("/addresses/{addressId}")
	AddressResponse patchAddress(@PathVariable long addressId, @Valid @RequestBody AddressRequest request) {
		return accountService.updateAddress(addressId, request);
	}

	@PutMapping("/addresses/{addressId}")
	AddressResponse updateAddress(@PathVariable long addressId, @Valid @RequestBody AddressRequest request) {
		return accountService.updateAddress(addressId, request);
	}

	@DeleteMapping("/addresses/{addressId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteAddress(@PathVariable long addressId) {
		accountService.deleteAddress(addressId);
	}
}
