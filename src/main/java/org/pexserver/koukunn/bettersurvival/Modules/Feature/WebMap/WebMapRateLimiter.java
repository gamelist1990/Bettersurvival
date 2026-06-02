package org.pexserver.koukunn.bettersurvival.Modules.Feature.WebMap;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WebMapRateLimiter {
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int burst;
    private final double refillPerSecond;

    public WebMapRateLimiter(int burst, double refillPerSecond) {
        this.burst = Math.max(1, burst);
        this.refillPerSecond = Math.max(0.1D, refillPerSecond);
    }

    public boolean allow(String key) {
        long now = Instant.now().toEpochMilli();
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(burst, now));
        synchronized (bucket) {
            double elapsedSeconds = Math.max(0D, (now - bucket.lastRefillAt) / 1000D);
            bucket.tokens = Math.min(burst, bucket.tokens + (elapsedSeconds * refillPerSecond));
            bucket.lastRefillAt = now;
            if (bucket.tokens < 1D) {
                return false;
            }
            bucket.tokens -= 1D;
            return true;
        }
    }

    private static final class Bucket {
        private double tokens;
        private long lastRefillAt;

        private Bucket(double tokens, long lastRefillAt) {
            this.tokens = tokens;
            this.lastRefillAt = lastRefillAt;
        }
    }
}
