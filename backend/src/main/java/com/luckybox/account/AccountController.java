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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/account")
class AccountController {

	private final AccountService accountService;

	AccountController(AccountService accountService) {
		this.accountService = accountService;
	}

	@GetMapping("/addresses")
	List<AddressResponse> addresses() {
		return accountService.addresses();
	}

	@GetMapping("/profile")
	AuthUser profile() {
		return accountService.currentUser();
	}

	@PatchMapping("/profile")
	AuthUser patchProfile(@Valid @RequestBody ProfileRequest request, HttpServletRequest httpRequest) {
		return accountService.updateProfile(request, httpRequest);
	}

	@PutMapping("/profile")
	AuthUser updateProfile(@Valid @RequestBody ProfileRequest request, HttpServletRequest httpRequest) {
		return accountService.updateProfile(request, httpRequest);
	}

	@PostMapping("/addresses")
	@ResponseStatus(HttpStatus.CREATED)
	AddressResponse createAddress(@Valid @RequestBody AddressRequest request) {
		return accountService.createAddress(request);
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
