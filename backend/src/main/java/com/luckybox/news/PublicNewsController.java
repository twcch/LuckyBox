package com.luckybox.news;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/news")
class PublicNewsController {

	private final NewsService newsService;

	PublicNewsController(NewsService newsService) {
		this.newsService = newsService;
	}

	@GetMapping
	List<NewsSummary> news() {
		return newsService.publishedNews();
	}

	@GetMapping("/{slug}")
	NewsDetail newsDetail(@PathVariable String slug) {
		return newsService.publishedNewsDetail(slug);
	}
}
