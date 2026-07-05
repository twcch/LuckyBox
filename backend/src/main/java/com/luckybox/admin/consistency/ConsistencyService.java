package com.luckybox.admin.consistency;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.luckybox.account.SecurityPrincipals;
import com.luckybox.audit.AuditLogHelper;
import com.luckybox.common.ApiException;

@Service
class ConsistencyService {

	private final ConsistencyRepository consistencyRepository;
	private final AuditLogHelper auditLogHelper;

	ConsistencyService(ConsistencyRepository consistencyRepository, AuditLogHelper auditLogHelper) {
		this.consistencyRepository = consistencyRepository;
		this.auditLogHelper = auditLogHelper;
	}

	/** Admin-facing report of all current data inconsistencies. */
	ConsistencyReportResponse report() {
		requireAdmin();
		return buildReport();
	}

	/** System scan (used by the daily job): records each finding to the audit log. Returns the count. */
	int auditScan() {
		ConsistencyReportResponse report = buildReport();
		for (ConsistencyFinding finding : report.findings()) {
			auditLogHelper.recordSystemAction(
					"DATA_INCONSISTENCY_DETECTED",
					"KujiCampaign",
					String.valueOf(finding.campaignId()),
					"{\"code\":\"" + finding.code() + "\",\"slug\":\"" + escapeJson(finding.slug())
							+ "\",\"detail\":\"" + escapeJson(finding.detail()) + "\"}");
		}
		return report.findingCount();
	}

	private ConsistencyReportResponse buildReport() {
		List<ConsistencyFinding> findings = new ArrayList<>();
		findings.addAll(consistencyRepository.findRemainingTicketMismatches());
		findings.addAll(consistencyRepository.findDrawnResultMismatches());
		findings.addAll(consistencyRepository.findUnrevealedSoldOutCampaigns());
		findings.addAll(consistencyRepository.findPrizeQuantityMismatches());
		return new ConsistencyReportResponse(
				Instant.now().toString(),
				consistencyRepository.countCampaigns(),
				findings.size(),
				findings);
	}

	private static String escapeJson(String value) {
		if (value == null) {
			return "";
		}
		return value.replace("\\", "\\\\").replace("\"", "\\\"")
				.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
	}

	private void requireAdmin() {
		SecurityPrincipals.requireAdmin();
	}
}
