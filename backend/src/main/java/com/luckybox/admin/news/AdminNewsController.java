package com.luckybox.admin.news;

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
@RequestMapping("/api/admin/news")
class AdminNewsController {

	private final AdminNewsService adminNewsService;

	AdminNewsController(AdminNewsService adminNewsService) {
		this.adminNewsService = adminNewsService;
	}

	@GetMapping
	List<AdminNewsResponse> news(
			@RequestParam(required = false) String status,
			@RequestParam(required = false, name = "q") String keyword) {
		return adminNewsService.news(status, keyword);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	AdminNewsResponse createNews(@Valid @RequestBody AdminNewsRequest request) {
		return adminNewsService.createNews(request);
	}

	@PatchMapping("/{newsId}")
	AdminNewsResponse updateNews(@PathVariable long newsId, @Valid @RequestBody AdminNewsRequest request) {
		return adminNewsService.updateNews(newsId, request);
	}
}
