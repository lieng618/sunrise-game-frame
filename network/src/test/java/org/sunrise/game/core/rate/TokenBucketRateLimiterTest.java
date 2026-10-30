package org.sunrise.game.core.rate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenBucketRateLimiterTest {
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    @Test
    void exhaustionPersistsAndRefillsContinuously() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(3, 60, 0L);

        assertTrue(limiter.tryAcquire(0));
        assertTrue(limiter.tryAcquire(0));
        assertTrue(limiter.tryAcquire(0));
        assertFalse(limiter.tryAcquire(0));

        assertFalse(limiter.tryAcquire(NANOS_PER_SECOND / 2));
        assertTrue(limiter.tryAcquire(NANOS_PER_SECOND));
        assertFalse(limiter.tryAcquire(NANOS_PER_SECOND));
    }

    @Test
    void failedAcquisitionDoesNotResetTheBucket() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 60, 0L);

        assertTrue(limiter.tryAcquire(0));
        assertFalse(limiter.tryAcquire(0));
        assertFalse(limiter.tryAcquire(NANOS_PER_SECOND / 2));
        assertTrue(limiter.tryAcquire(NANOS_PER_SECOND));
        assertFalse(limiter.tryAcquire(NANOS_PER_SECOND));
    }

    @Test
    void idleTimeCannotExceedCapacity() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(3, 60, 0L);

        long now = 100 * NANOS_PER_SECOND;
        assertTrue(limiter.tryAcquire(now));
        assertTrue(limiter.tryAcquire(now));
        assertTrue(limiter.tryAcquire(now));
        assertFalse(limiter.tryAcquire(now));
    }

    @Test
    void burstBudgetCoversConfiguredRate() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(120, 1200, 0L);

        for (int i = 0; i < 120; i++) {
            assertTrue(limiter.tryAcquire(0));
        }
        assertFalse(limiter.tryAcquire(0));

        long afterOneMinute = 60 * NANOS_PER_SECOND;
        for (int i = 0; i < 120; i++) {
            assertTrue(limiter.tryAcquire(afterOneMinute));
        }
    }
}
