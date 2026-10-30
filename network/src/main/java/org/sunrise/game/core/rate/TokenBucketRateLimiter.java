package org.sunrise.game.core.rate;

/**
 * Lock-protected token bucket with lazy, continuous refill.
 *
 * <p>The bucket starts full. Every successful acquisition consumes one token.
 * Refill is calculated from elapsed nanoseconds when the limiter is accessed,
 * so no scheduled task is required.</p>
 */
public final class TokenBucketRateLimiter {
    private static final double NANOS_PER_MINUTE = 60_000_000_000.0;

    private final int capacity;
    private final double refillPerNano;
    private double tokens;
    private long lastRefillNanos;

    public TokenBucketRateLimiter(int capacity, int refillPerMinute) {
        this(capacity, refillPerMinute, System.nanoTime());
    }

    TokenBucketRateLimiter(int capacity, int refillPerMinute, long nowNanos) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        if (refillPerMinute <= 0) {
            throw new IllegalArgumentException("refillPerMinute must be positive");
        }
        this.capacity = capacity;
        this.refillPerNano = refillPerMinute / NANOS_PER_MINUTE;
        this.tokens = capacity;
        this.lastRefillNanos = nowNanos;
    }

    public boolean tryAcquire() {
        return tryAcquire(System.nanoTime());
    }

    public synchronized boolean tryAcquire(long nowNanos) {
        long elapsedNanos = nowNanos - lastRefillNanos;
        if (elapsedNanos > 0) {
            tokens = Math.min(capacity, tokens + elapsedNanos * refillPerNano);
            lastRefillNanos = nowNanos;
        }
        if (tokens < 1.0) {
            return false;
        }
        tokens -= 1.0;
        return true;
    }

}
