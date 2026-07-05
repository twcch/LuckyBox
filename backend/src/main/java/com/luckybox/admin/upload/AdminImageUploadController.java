package com.luckybox.admin.upload;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/uploads")
class AdminImageUploadController {

	private final AdminImageUploadService adminImageUploadService;

	AdminImageUploadController(AdminImageUploadService adminImageUploadService) {
		this.adminImageUploadService = adminImageUploadService;
	}

	@PostMapping(path = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	AdminImageUploadResponse uploadImage(@RequestParam("file") MultipartFile file) {
		return adminImageUploadService.uploadImage(file);
	}
}
