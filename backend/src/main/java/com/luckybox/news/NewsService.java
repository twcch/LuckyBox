package com.luckybox.news;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.luckybox.common.ApiException;

@Service
class NewsService {

	private final NewsRepository newsRepository;

	NewsService(NewsRepository newsRepository) {
		this.newsRepository = newsRepository;
	}

	List<NewsSummary> publishedNews() {
		return newsRepository.findPublishedNews();
	}

	NewsDetail publishedNewsDetail(String slug) {
		return newsRepository.findPublishedBySlug(slug)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NEWS_NOT_FOUND", "找不到指定公告。"));
	}
}
