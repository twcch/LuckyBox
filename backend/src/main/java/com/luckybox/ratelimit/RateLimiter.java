package com.luckybox.ratelimit;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 輕量級單機 in-memory token-bucket rate limiter（無外部依賴）。每個 key 一個 bucket，依設定
 * 容量與時間窗自然補充 token。適合單實例 SQLite 部署；多實例水平擴展需改用共享狀態（如 Redis）。
 */
@Component
public class RateLimiter {

	private static final long IDLE_EVICT_NANOS = Duration.ofMinutes(10).toNanos();

	private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

	/**
	 * 嘗試對指定 key 取得一個 token。capacity 為時間窗內允許的請求數。回傳 true 代表放行、false 代表超限。
	 */
	public boolean tryAcquire(String key, int capacity, Duration window) {
		if (capacity <= 0 || window.isZero() || window.isNegative()) {
			return true;
		}
		long now = System.nanoTime();
		Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(capacity, now));
		synchronized (bucket) {
			double refillPerNano = (double) capacity / window.toNanos();
			long elapsed = Math.max(0, now - bucket.lastRefillNanos);
			bucket.tokens = Math.min(capacity, bucket.tokens + elapsed * refillPerNano);
			bucket.lastRefillNanos = now;
			if (bucket.tokens >= 1.0) {
				bucket.tokens -= 1.0;
				return true;
			}
			return false;
		}
	}

	/** 定期清掉長時間未使用的 bucket，避免 key（IP/使用者）累積造成記憶體無限成長。 */
	@Scheduled(fixedDelayString = "${luckybox.ratelimit.cleanup-interval-ms:600000}")
	void evictIdleBuckets() {
		long now = System.nanoTime();
		buckets.entrySet().removeIf(entry -> now - entry.getValue().lastRefillNanos > IDLE_EVICT_NANOS);
	}

	private static final class Bucket {
		private double tokens;
		// volatile：tryAcquire 於 synchronized 內寫入，evictIdleBuckets 無鎖讀取，volatile 保證可見性。
		private volatile long lastRefillNanos;

		private Bucket(int capacity, long now) {
			this.tokens = capacity;
			this.lastRefillNanos = now;
		}
	}
}
