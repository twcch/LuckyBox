package com.luckybox.admin.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminUserRoleRequest(
		@NotBlank(message = "會員角色不可空白")
		@Size(max = 40, message = "會員角色不可超過 40 字")
		String role) {
}
