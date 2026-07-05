package com.luckybox.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class LaunchReadinessArtifactsTests {

	private static final Path REPO_ROOT = Path.of("..");

	@Test
	void launchReadinessDocumentRecordsProviderDecisionAndExternalGates() throws IOException {
		String readiness = read("docs/launch-readiness.md");

		assertThat(readiness)
				.contains("ECPay first")
				.contains("NewebPay as fallback")
				.contains("ECPay AioCheckOut adapter")
				.contains("CheckMacValue")
					.contains("Launch Sign-Off Register")
					.contains("scripts/check-launch-readiness.sh --env .env.production")
					.contains("scripts/generate-launch-evidence-template.sh --env .env.production --out launch-evidence.md")
					.contains("scripts/backup-luckybox.sh")
					.contains("scripts/smoke-test.sh")
					.contains("Legal counsel reviewed")
				.contains("Legal-review feedback has been applied")
				.contains("LUCKYBOX_PAYMENT_ECPAY_CREDIT_INSTALLMENT")
				.contains("Payment provider contract is approved")
				.contains("Product source, official-license claims, image rights")
				.contains("Company registration, invoice policy, logistics provider, shipping owner");
	}

	@Test
	void paymentProviderExpansionDocumentRecordsPhaseTwoAdaptersAndLaunchGates() throws IOException {
		String expansion = read("docs/payment-provider-expansion.md");

		assertThat(expansion)
				.contains("ECPay credit-card installment checkout: implemented")
				.contains("LUCKYBOX_PAYMENT_ECPAY_CREDIT_INSTALLMENT")
				.contains("LINE Pay redirect checkout: implemented")
				.contains("JKo Pay redirect checkout: implemented")
				.contains("LUCKYBOX_PAYMENT_LINEPAY_CALLBACK_TESTED=true")
				.contains("LUCKYBOX_PAYMENT_JKOPAY_CALLBACK_TESTED=true")
				.contains("Do not set those flags until the real merchant dashboard callback flow");
	}

	@Test
	void launchSignoffRegisterMapsExternalQuestionsToEnvGates() throws IOException {
		String register = read("docs/launch-signoff-register.md");

		assertThat(register)
				.contains("LuckyBox Launch Sign-Off Register")
				.contains("LUCKYBOX_BUSINESS_REGISTRATION_APPROVED")
				.contains("LUCKYBOX_LEGAL_COUNSEL_ASSIGNED")
				.contains("LUCKYBOX_LEGAL_FEEDBACK_APPLIED")
				.contains("LUCKYBOX_PRODUCT_SOURCE_APPROVED")
				.contains("LUCKYBOX_BRAND_COPY_APPROVED")
				.contains("LUCKYBOX_INVOICE_POLICY_APPROVED")
				.contains("LUCKYBOX_SHIPPING_OWNER_ASSIGNED")
				.contains("LUCKYBOX_LOGISTICS_PROVIDER_APPROVED")
				.contains("LUCKYBOX_CONVENIENCE_STORE_PICKUP_POLICY_APPROVED")
				.contains("LUCKYBOX_INTERNATIONAL_SHIPPING_POLICY_APPROVED")
				.contains("LUCKYBOX_PREORDER_POLICY_APPROVED")
					.contains("LUCKYBOX_DEPLOYMENT_OWNER")
					.contains("LUCKYBOX_ROLLBACK_OWNER")
					.contains("LUCKYBOX_PRODUCTION_SMOKE_TEST_DONE")
					.contains("LUCKYBOX_PAYMENT_LINEPAY_CHANNEL_ID")
					.contains("LUCKYBOX_PAYMENT_LINEPAY_CALLBACK_TESTED=true")
					.contains("LUCKYBOX_PAYMENT_JKOPAY_STORE_ID")
					.contains("LUCKYBOX_PAYMENT_JKOPAY_CALLBACK_TESTED=true")
					.contains("scripts/generate-launch-evidence-template.sh --env .env.production --out");
	}

	@Test
	void sopsCoverSupportShippingTakedownAndRefundWorkflows() throws IOException {
		assertSop("docs/sops/customer-support.md", "Customer Support SOP", "Done Criteria");
		assertSop("docs/sops/shipping.md", "Shipping SOP", "Returned Package");
		assertSop("docs/sops/emergency-takedown.md", "Emergency Takedown SOP", "Restart Criteria");
		assertSop("docs/sops/refund-compensation.md", "Refund And Compensation SOP", "Approval request");
	}

	@Test
	void operationalScriptsUseSafeShellDefaultsAndExpectedTools() throws IOException {
		String readinessScript = read("scripts/check-launch-readiness.sh");
		String backupScript = read("scripts/backup-luckybox.sh");
		String smokeScript = read("scripts/smoke-test.sh");
		String evidenceScript = read("scripts/generate-launch-evidence-template.sh");

		assertShellScript(readinessScript);
		assertShellScript(backupScript);
		assertShellScript(smokeScript);
		assertShellScript(evidenceScript);

		assertThat(readinessScript)
				.contains("LUCKYBOX_LEGAL_REVIEW_APPROVED")
				.contains("LUCKYBOX_LEGAL_FEEDBACK_APPLIED")
				.contains("LUCKYBOX_BUSINESS_REGISTRATION_APPROVED")
				.contains("LUCKYBOX_PRODUCT_SOURCE_APPROVED")
				.contains("LUCKYBOX_BRAND_COPY_APPROVED")
				.contains("LUCKYBOX_INVOICE_POLICY_APPROVED")
				.contains("LUCKYBOX_PAYMENT_ECPAY_ENABLED")
				.contains("LUCKYBOX_PAYMENT_ECPAY_HASH_KEY")
				.contains("LUCKYBOX_PAYMENT_ECPAY_ACTION_URL")
				.contains("LUCKYBOX_PAYMENT_ECPAY_CREDIT_INSTALLMENT")
				.contains("LUCKYBOX_PAYMENT_ECPAY_INSTALLMENT_CONTRACT_APPROVED")
				.contains("LUCKYBOX_PAYMENT_LINEPAY_CALLBACK_TESTED")
				.contains("LUCKYBOX_PAYMENT_JKOPAY_CALLBACK_TESTED")
				.contains("LUCKYBOX_SHIPPING_OWNER")
				.contains("LUCKYBOX_LOGISTICS_PROVIDER")
				.contains("LUCKYBOX_PRODUCTION_SMOKE_TEST_DONE")
				.contains("LUCKYBOX_BACKUP_RESTORE_DRILL_DONE")
				.doesNotContain("rm -rf");
		assertThat(backupScript)
				.contains("sqlite3")
				.contains(".backup")
				.contains("SHA-256")
				.doesNotContain("rm -rf");
		assertThat(smokeScript)
				.contains("curl")
				.contains("API_BASE_URL=\"$BASE_URL/api\"")
				.contains("$API_BASE_URL/health")
				.contains("/actuator/health")
				.doesNotContain("rm -rf");
		assertThat(evidenceScript)
				.contains("LuckyBox Launch Evidence")
				.contains("LUCKYBOX_PAYMENT_LINEPAY_CALLBACK_TESTED")
				.contains("LUCKYBOX_PAYMENT_JKOPAY_CALLBACK_TESTED")
				.contains("_redacted_")
				.doesNotContain("rm -rf");
	}

	@Test
	void envExampleContainsProductionPlaceholdersWithoutRealSecrets() throws IOException {
		String envExample = read(".env.example");

		assertThat(envExample)
				.contains("# SPRING_PROFILES_ACTIVE=prod")
				.contains("# LUCKYBOX_PAYMENT_PROVIDER=ECPAY")
				.contains("# LUCKYBOX_PAYMENT_ECPAY_ENABLED=true")
				.contains("# LUCKYBOX_PAYMENT_ECPAY_HASH_KEY=REPLACE_WITH_ECPAY_HASH_KEY")
				.contains("# LUCKYBOX_PAYMENT_ECPAY_ACTION_URL=https://payment.ecpay.com.tw/Cashier/AioCheckOut/V5")
				.contains("# LUCKYBOX_PAYMENT_ECPAY_CREDIT_INSTALLMENT=3,6")
				.contains("# LUCKYBOX_PAYMENT_ECPAY_INSTALLMENT_CONTRACT_APPROVED=false")
				.contains("# LUCKYBOX_PAYMENT_LINEPAY_CHANNEL_ID=REPLACE_WITH_LINEPAY_CHANNEL_ID")
				.contains("# LUCKYBOX_PAYMENT_LINEPAY_CALLBACK_TESTED=false")
				.contains("# LUCKYBOX_PAYMENT_JKOPAY_API_KEY=REPLACE_WITH_JKOPAY_API_KEY")
				.contains("# LUCKYBOX_PAYMENT_JKOPAY_CALLBACK_TESTED=false")
				.contains("# LUCKYBOX_PAYMENT_NEWEBPAY_HASH_KEY=REPLACE_WITH_NEWEBPAY_HASH_KEY")
				.contains("# LUCKYBOX_BUSINESS_REGISTRATION_APPROVED=false")
				.contains("# LUCKYBOX_LEGAL_COUNSEL_ASSIGNED=false")
				.contains("# LUCKYBOX_LEGAL_REVIEW_APPROVED=false")
				.contains("# LUCKYBOX_LEGAL_FEEDBACK_APPLIED=false")
				.contains("# LUCKYBOX_PRODUCT_SOURCE_APPROVED=false")
				.contains("# LUCKYBOX_BRAND_COPY_APPROVED=false")
				.contains("# LUCKYBOX_INVOICE_POLICY_APPROVED=false")
				.contains("# LUCKYBOX_SHIPPING_OWNER=REPLACE_WITH_SHIPPING_OWNER")
				.contains("# LUCKYBOX_LOGISTICS_PROVIDER=REPLACE_WITH_LOGISTICS_PROVIDER")
				.contains("# LUCKYBOX_PRODUCTION_SMOKE_TEST_DONE=false")
				.contains("# LUCKYBOX_LAUNCH_CHECKLIST_APPROVED=false")
				.doesNotContain("dev-mock-webhook-secret")
				.doesNotContain("HashKey=")
				.doesNotContain("HashIV=");
	}

	@Test
	void projectPlanRecordsReadinessWithoutClaimingExternalLaunchIsDone() throws IOException {
			String plan = read("PROJECT_DEVELOPMENT_PLAN.md");

			assertThat(plan)
					.contains("ECPay first / NewebPay fallback")
					.contains("docs/launch-readiness.md")
					.contains("docs/launch-signoff-register.md")
					.contains("scripts/generate-launch-evidence-template.sh")
					.contains("正式法務審閱、正式金流開通、正式商品素材授權、正式部署、小流量真實測試與上線公告仍保留 Milestone 5 外部簽核")
					.contains("- [ ] 正式金流開通。")
				.contains("- [ ] 正式環境部署。");
	}

	private static void assertSop(String path, String title, String requiredSection) throws IOException {
		assertThat(read(path))
				.contains(title)
				.contains(requiredSection);
	}

	private static void assertShellScript(String content) {
		assertThat(content)
				.startsWith("#!/usr/bin/env bash")
				.contains("set -euo pipefail");
	}

	private static String read(String relativePath) throws IOException {
		return Files.readString(REPO_ROOT.resolve(relativePath));
	}
}
