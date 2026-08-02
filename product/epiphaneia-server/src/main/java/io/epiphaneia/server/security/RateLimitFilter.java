package io.epiphaneia.server.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Rate-limiting filter using Bucket4j.
 * <p>
 * Limits:
 * - Login endpoint: 5 requests/minute per IP
 * - All other API endpoints: 100 requests/minute per IP
 * <p>
 * Rate limit keys are per-IP. Exceeding the limit returns HTTP 429.
 * Idle buckets (no requests for {@value #IDLE_TIMEOUT_MINUTES} minutes) are
 * periodically evicted so the maps cannot grow unboundedly.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int LOGIN_LIMIT = 5;
    private static final int API_LIMIT = 100;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int IDLE_TIMEOUT_MINUTES = 60;
    private static final int CLEANUP_INTERVAL_MINUTES = 10;
    private static final long IDLE_TIMEOUT_NANOS = TimeUnit.MINUTES.toNanos(IDLE_TIMEOUT_MINUTES);

    private final Map<String, BucketEntry> loginBuckets = new ConcurrentHashMap<>();
    private final Map<String, BucketEntry> apiBuckets = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupScheduler;

    public RateLimitFilter() {
        cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rate-limit-cleanup");
            t.setDaemon(true);
            return t;
        });
        cleanupScheduler.scheduleWithFixedDelay(
                this::cleanupIdleBuckets, CLEANUP_INTERVAL_MINUTES, CLEANUP_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String ip = getClientIp(request);
        long now = System.nanoTime();

        boolean isLogin = path.endsWith("/auth/login");
        Bucket bucket = entryFor(
                isLogin ? loginBuckets : apiBuckets,
                ip,
                isLogin ? LOGIN_LIMIT : API_LIMIT,
                now).bucket;

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":{\"code\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"Too many requests. Try again later.\"}}");
        }
    }

    private static BucketEntry entryFor(Map<String, BucketEntry> buckets, String ip, int limit, long now) {
        BucketEntry entry = buckets.get(ip);
        if (entry == null) {
            entry = new BucketEntry(createBucket(limit), now);
            BucketEntry existing = buckets.putIfAbsent(ip, entry);
            if (existing != null) {
                entry = existing;
            }
        }
        entry.lastAccessNanos = now;
        return entry;
    }

    private void cleanupIdleBuckets() {
        long cutoff = System.nanoTime() - IDLE_TIMEOUT_NANOS;
        loginBuckets.entrySet().removeIf(e -> e.getValue().lastAccessNanos < cutoff);
        apiBuckets.entrySet().removeIf(e -> e.getValue().lastAccessNanos < cutoff);
    }

    private static Bucket createBucket(int limit) {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(limit, Refill.intervally(limit, WINDOW)))
                .build();
    }

    private static final class BucketEntry {
        final Bucket bucket;
        // volatile: written by request threads, read by the cleanup daemon thread
        volatile long lastAccessNanos;

        BucketEntry(Bucket bucket, long lastAccessNanos) {
            this.bucket = bucket;
            this.lastAccessNanos = lastAccessNanos;
        }
    }

    private static String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        String remoteAddr = request.getRemoteAddr();
        if (xff != null && !xff.isBlank() && isTrustedProxy(remoteAddr)) {
            return xff.split(",")[0].trim();
        }
        return remoteAddr;
    }

    private static boolean isTrustedProxy(String addr) {
        if ("127.0.0.1".equals(addr)) return true;
        return addr.startsWith("172.") || addr.startsWith("10.");
    }
}
