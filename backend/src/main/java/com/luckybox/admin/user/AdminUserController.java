package com.luckybox.admin.user;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/users")
class AdminUserController {

	private final AdminUserService adminUserService;

	AdminUserController(AdminUserService adminUserService) {
		this.adminUserService = adminUserService;
	}

	@GetMapping
	List<AdminUserResponse> users(
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String role,
			@RequestParam(required = false, name = "q") String keyword) {
		return adminUserService.users(status, role, keyword);
	}

	@GetMapping("/{userId}")
	AdminMemberDetailResponse memberDetail(
			@PathVariable long userId,
			@RequestParam(defaultValue = "false") boolean reveal) {
		return adminUserService.memberDetail(userId, reveal);
	}

	@PostMapping("/{userId}/notes")
	@ResponseStatus(HttpStatus.CREATED)
	AdminMemberDetailResponse.Note addMemberNote(
			@PathVariable long userId,
			@Valid @RequestBody MemberNoteRequest request) {
		return adminUserService.addMemberNote(userId, request);
	}

	@PostMapping("/{userId}/compensation")
	@ResponseStatus(HttpStatus.CREATED)
	CompensationResponse grantCompensation(
			@PathVariable long userId,
			@Valid @RequestBody CompensationRequest request) {
		return adminUserService.grantCompensation(userId, request);
	}

	@PatchMapping("/{userId}/status")
	AdminUserResponse updateStatus(
			@PathVariable long userId,
			@Valid @RequestBody AdminUserStatusRequest request) {
		return adminUserService.updateStatus(userId, request);
	}

	@PatchMapping("/{userId}/role")
	AdminUserResponse updateRole(
			@PathVariable long userId,
			@Valid @RequestBody AdminUserRoleRequest request) {
		return adminUserService.updateRole(userId, request);
	}
}
