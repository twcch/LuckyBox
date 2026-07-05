package com.luckybox.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class RequestBodyValidationPolicyTests {

	@Test
	void everyRequestBodyParameterUsesBeanValidation() throws IOException {
		List<String> missingValidation = new ArrayList<>();
		try (var paths = Files.walk(Path.of("src", "main", "java", "com", "luckybox"))) {
			for (Path path : paths
					.filter(Files::isRegularFile)
					.filter(candidate -> candidate.toString().endsWith(".java"))
					.toList()) {
				List<String> lines = Files.readAllLines(path);
				for (int index = 0; index < lines.size(); index++) {
					String line = lines.get(index);
					if (line.contains("@RequestBody") && !line.contains("@Valid")) {
						missingValidation.add(path + ":" + (index + 1) + " -> " + line.strip());
					}
				}
			}
		}

		assertThat(missingValidation)
				.as("Request body DTOs must pass through Jakarta Bean Validation before service logic")
				.isEmpty();
	}
}
