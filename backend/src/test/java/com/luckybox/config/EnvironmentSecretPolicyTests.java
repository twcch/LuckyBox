package com.luckybox.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class EnvironmentSecretPolicyTests {

	@Test
	void environmentFilesAndLocalDataAreIgnoredAtEveryProjectLevel() throws IOException {
		assertIgnoreRules(Path.of("..", ".gitignore"),
				".env",
				".env.*",
				"!.env.example",
				"backend/data/*",
				"backend/uploads/",
				"*.sqlite",
				"*.db",
				"logs/");
		assertIgnoreRules(Path.of(".gitignore"),
				".env",
				".env.*",
				"!.env.example",
				"data/*",
				"uploads/",
				"*.sqlite",
				"*.db",
				"logs/");
		assertIgnoreRules(Path.of("..", "frontend", ".gitignore"),
				".env",
				".env.*",
				"!.env.example");
	}

	@Test
	void committedEnvironmentTemplateUsesPlaceholdersForSensitiveValues() throws IOException {
		String envExample = Files.readString(Path.of("..", ".env.example"));

		assertThat(envExample)
				.contains("LUCKYBOX_PAYMENT_MOCK_WEBHOOK_SECRET=REPLACE_WITH_LOCAL_ONLY_SECRET")
				.contains("LUCKYBOX_PAYMENT_ECPAY_HASH_KEY=REPLACE_WITH_ECPAY_HASH_KEY")
				.contains("LUCKYBOX_PAYMENT_NEWEBPAY_HASH_KEY=REPLACE_WITH_NEWEBPAY_HASH_KEY")
				.contains("SMTP_PASSWORD=REPLACE_WITH_SMTP_PASSWORD")
				.doesNotContain("dev-mock-webhook-secret")
				.doesNotContain("HashKey=")
				.doesNotContain("HashIV=")
				.doesNotContain("ChangeMe123!")
				.doesNotContain("admin@luckybox.local");
	}

	@Test
	void productionProfileKeepsDevelopmentSeedAndMockPaymentOffByDefault() throws IOException {
		String prodProperties = Files.readString(Path.of("src", "main", "resources", "application-prod.properties"));

		assertThat(prodProperties)
				.contains("luckybox.seed.enabled=false")
				.contains("luckybox.payment.mock-enabled=${LUCKYBOX_PAYMENT_MOCK_ENABLED:false}");
	}

	private static void assertIgnoreRules(Path path, String... expectedRules) throws IOException {
		String content = Files.readString(path);
		for (String expectedRule : expectedRules) {
			assertThat(content)
					.as("%s should contain %s", path, expectedRule)
					.contains(expectedRule);
		}
	}
}
