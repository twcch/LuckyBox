package com.luckybox.account;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
class AuthController {

	private final AccountService accountService;

	AuthController(AccountService accountService) {
		this.accountService = accountService;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	AuthUser register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
		return accountService.register(request, httpRequest);
	}

	@PostMapping("/login")
	AuthUser login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
		return accountService.login(request, httpRequest);
	}

	@GetMapping("/me")
	AuthUser me() {
		return accountService.currentUser();
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void logout(HttpServletRequest request) {
		accountService.logout(request);
	}
}
