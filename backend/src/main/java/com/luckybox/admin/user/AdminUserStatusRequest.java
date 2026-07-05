package com.luckybox.admin.user;

import jakarta.validation.constraints.NotBlank;

record AdminUserStatusRequest(@NotBlank String status) {
}
