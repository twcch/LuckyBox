package com.luckybox.fairness;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/campaigns")
class FairnessController {

	private final FairnessService fairnessService;

	FairnessController(FairnessService fairnessService) {
		this.fairnessService = fairnessService;
	}

	@GetMapping("/{slug}/fairness")
	FairnessResponse getFairness(@PathVariable String slug) {
		return fairnessService.getFairness(slug);
	}
}
