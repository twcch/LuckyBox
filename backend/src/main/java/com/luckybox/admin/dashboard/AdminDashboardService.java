package com.luckybox.admin.dashboard;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.luckybox.account.SecurityPrincipals;
import com.luckybox.common.ApiException;

@Service
class AdminDashboardService {

	private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Taipei");
	private static final String DECIMAL_PATTERN = "#,##0.#";

	private final AdminDashboardRepository adminDashboardRepository;

	AdminDashboardService(AdminDashboardRepository adminDashboardRepository) {
		this.adminDashboardRepository = adminDashboardRepository;
	}

	AdminDashboardResponse dashboard() {
		requireAdmin();
		String todayStart = LocalDate.now(BUSINESS_ZONE)
				.atStartOfDay(BUSINESS_ZONE)
				.toInstant()
				.toString();
		int todayTopUpAmount = adminDashboardRepository.todayTopUpAmount(todayStart);
		int todayDrawCount = adminDashboardRepository.todayDrawCount(todayStart);
		int todayNewUsers = adminDashboardRepository.todayNewUsers(todayStart);
		int todayActiveUsers = adminDashboardRepository.todayActiveUsers(todayStart);
		int liveCampaigns = adminDashboardRepository.liveCampaigns();
		int nearSoldCampaigns = adminDashboardRepository.nearSoldCampaigns();
		int requestedShipments = adminDashboardRepository.requestedShipments();
		int failedPayments = adminDashboardRepository.failedPayments();
		int supportQueue = adminDashboardRepository.supportQueue();
		int drawAlerts = adminDashboardRepository.todayFailedDrawOrders(todayStart)
				+ adminDashboardRepository.dataConsistencyFindings();
		int todayAuditLogs = adminDashboardRepository.todayAuditLogs(todayStart);
		List<AdminDashboardMetricResponse> productMetrics = productMetrics(todayStart, todayTopUpAmount, todayDrawCount);
		return new AdminDashboardResponse(
				List.of(
						new AdminDashboardMetricResponse("todayGmv", "今日營收", money(todayTopUpAmount), "已付款儲值訂單", "danger"),
						new AdminDashboardMetricResponse("todayDraws", "今日抽數", count(todayDrawCount), "已完成 DrawOrder", "ink"),
						new AdminDashboardMetricResponse("todayUsers", "今日新會員", count(todayNewUsers), "新註冊帳號", "teal"),
						new AdminDashboardMetricResponse("todayActiveUsers", "今日活躍會員", count(todayActiveUsers), "今日有儲值或抽賞", "teal"),
						new AdminDashboardMetricResponse("liveCampaigns", "開抽中賞池", count(liveCampaigns), "目前可抽", "ink"),
						new AdminDashboardMetricResponse("nearSoldCampaigns", "即將售完賞池", count(nearSoldCampaigns), "剩餘 10% 或 10 張內", nearSoldCampaigns > 0 ? "warning" : "teal"),
						new AdminDashboardMetricResponse("requestedShipments", "未出貨", count(requestedShipments), "等待後台處理", requestedShipments > 0 ? "warning" : "teal"),
						new AdminDashboardMetricResponse("supportQueue", "客服待處理", count(supportQueue), "願望審核與付款追蹤", supportQueue > 0 ? "warning" : "teal"),
						new AdminDashboardMetricResponse("failedPayments", "付款失敗", count(failedPayments), "需人工追蹤", failedPayments > 0 ? "warning" : "ink"),
						new AdminDashboardMetricResponse("drawAlerts", "異常抽賞告警", count(drawAlerts), "失敗抽賞與資料一致性", drawAlerts > 0 ? "danger" : "teal"),
						new AdminDashboardMetricResponse("auditLogsToday", "今日審計紀錄", count(todayAuditLogs), "後台與系統操作", "ink")),
				productMetrics,
				adminDashboardRepository.latestRequestedShipments(),
				adminDashboardRepository.recentActivities());
	}

	private List<AdminDashboardMetricResponse> productMetrics(String todayStart, int todayTopUpAmount, int todayDrawCount) {
		int totalUsers = adminDashboardRepository.totalUsers();
		int visitorCount = adminDashboardRepository.visitorCount();
		int registeredVisitorCount = adminDashboardRepository.registeredVisitorCount();
		int paidTopUpUsers = adminDashboardRepository.paidTopUpUsers();
		int completedDrawUsers = adminDashboardRepository.completedDrawUsers();
		int totalPaidTopUpAmount = adminDashboardRepository.totalPaidTopUpAmount();
		int totalCompletedDrawQuantity = adminDashboardRepository.totalCompletedDrawQuantity();
		Double averageSoldOutHours = adminDashboardRepository.averageSoldOutHours();
		int totalUserPrizes = adminDashboardRepository.totalUserPrizes();
		int shipmentRequestedPrizes = adminDashboardRepository.shipmentRequestedPrizes();
		int todaySupportCases = adminDashboardRepository.todaySupportCases(todayStart);
		int refundOrCompensationCases = adminDashboardRepository.refundOrCompensationCases();
		int successfulPaymentOrders = adminDashboardRepository.successfulPaymentOrders();
		int paymentOrderCount = adminDashboardRepository.paymentOrderCount();
		int failedPaymentOrderCount = adminDashboardRepository.failedPaymentOrderCount();
		int drawOrderCount = adminDashboardRepository.drawOrderCount();
		int failedDrawOrderCount = adminDashboardRepository.failedDrawOrderCount();
		String drawApiErrorRate = percent(failedDrawOrderCount, drawOrderCount);
		return List.of(
				new AdminDashboardMetricResponse("visitorToRegistration", "訪客→註冊",
						percent(registeredVisitorCount, visitorCount), "已註冊訪客 / 匿名訪客", conversionTone(registeredVisitorCount, visitorCount)),
				new AdminDashboardMetricResponse("registrationToTopUp", "註冊→首儲",
						percent(paidTopUpUsers, totalUsers), "付費會員 / 註冊會員", conversionTone(paidTopUpUsers, totalUsers)),
				new AdminDashboardMetricResponse("topUpToDraw", "首儲→首抽",
						percent(completedDrawUsers, paidTopUpUsers), "已抽會員 / 付費會員", conversionTone(completedDrawUsers, paidTopUpUsers)),
				new AdminDashboardMetricResponse("dailyDraws", "每日抽數", count(todayDrawCount), "今日完成抽賞張數", "ink"),
				new AdminDashboardMetricResponse("dailyTopUpAmount", "每日儲值", money(todayTopUpAmount), "今日已付款儲值", "danger"),
				new AdminDashboardMetricResponse("arppu", "ARPPU",
						paidTopUpUsers == 0 ? "N/A" : money((int) Math.round((double) totalPaidTopUpAmount / paidTopUpUsers)),
						"全部已付款 / 付費會員", paidTopUpUsers == 0 ? "warning" : "teal"),
				new AdminDashboardMetricResponse("averageDrawsPerUser", "平均每人抽數",
						decimalRatio(totalCompletedDrawQuantity, completedDrawUsers), "完成抽數 / 已抽會員", completedDrawUsers == 0 ? "warning" : "ink"),
				new AdminDashboardMetricResponse("soldOutTime", "售完時間",
						averageSoldOutHours == null ? "N/A" : hours(averageSoldOutHours), "售完賞池平均小時", averageSoldOutHours == null ? "warning" : "teal"),
				new AdminDashboardMetricResponse("prizeShipmentRequestRate", "出貨申請率",
						percent(shipmentRequestedPrizes, totalUserPrizes), "已申請戰利品 / 全部戰利品", conversionTone(shipmentRequestedPrizes, totalUserPrizes)),
				new AdminDashboardMetricResponse("supportCases", "客服案件數", count(todaySupportCases), "今日備註/補償/退款/退換貨", todaySupportCases > 0 ? "warning" : "teal"),
				new AdminDashboardMetricResponse("refundCompensationRate", "退款/補償率",
						percent(refundOrCompensationCases, successfulPaymentOrders), "退款與補償 / 已付款訂單", refundOrCompensationCases > 0 ? "warning" : "teal"),
				new AdminDashboardMetricResponse("paymentFailureRate", "金流失敗率",
						percent(failedPaymentOrderCount, paymentOrderCount), "失敗付款 / 全部付款訂單", failedPaymentOrderCount > 0 ? "warning" : "teal"),
				new AdminDashboardMetricResponse("drawApiErrorRate", "抽賞錯誤率",
						drawApiErrorRate, "失敗抽賞 / 全部抽賞訂單", failedDrawOrderCount > 0 ? "danger" : "teal"),
				new AdminDashboardMetricResponse("drawApiP95Latency", "抽賞 p95",
						p95Latency(), "完成抽賞 API 延遲", "ink"));
	}

	private static String money(int amount) {
		return "NT$ " + NumberFormat.getIntegerInstance().format(amount);
	}

	private static String count(int count) {
		return NumberFormat.getIntegerInstance().format(count);
	}

	private static String percent(int numerator, int denominator) {
		if (denominator <= 0) {
			return "N/A";
		}
		return decimal((double) numerator * 100 / denominator) + "%";
	}

	private static String decimalRatio(int numerator, int denominator) {
		if (denominator <= 0) {
			return "N/A";
		}
		return decimal((double) numerator / denominator);
	}

	private static String hours(double hours) {
		if (hours < 24) {
			return decimal(Math.max(hours, 0)) + " 小時";
		}
		return decimal(hours / 24) + " 天";
	}

	private String p95Latency() {
		List<Double> latencies = adminDashboardRepository.completedDrawLatenciesMillis();
		if (latencies.isEmpty()) {
			return "N/A";
		}
		int index = Math.max(0, (int) Math.ceil(latencies.size() * 0.95) - 1);
		double latencyMillis = Math.max(latencies.get(index), 0);
		if (latencyMillis < 1_000) {
			return decimal(latencyMillis) + " ms";
		}
		return decimal(latencyMillis / 1_000) + " 秒";
	}

	private static String decimal(double value) {
		return new DecimalFormat(DECIMAL_PATTERN).format(value);
	}

	private static String conversionTone(int numerator, int denominator) {
		if (denominator <= 0) {
			return "warning";
		}
		return numerator > 0 ? "teal" : "warning";
	}

	private void requireAdmin() {
		SecurityPrincipals.requireAdmin();
	}
}
