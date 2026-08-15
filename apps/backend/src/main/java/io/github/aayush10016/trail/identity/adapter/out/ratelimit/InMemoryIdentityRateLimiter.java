package io.github.aayush10016.trail.identity.adapter.out.ratelimit;

import io.github.aayush10016.trail.identity.domain.model.IdentityRateLimitAttempt;
import io.github.aayush10016.trail.identity.domain.port.IdentityRateLimiter;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-local identity abuse-control adapter.
 *
 * <p>This adapter is intentionally conservative and bounded for private-beta
 * deployment. It does not provide cross-instance limits; the domain remains
 * behind a port so a distributed implementation can replace it later without
 * changing identity use cases.
 */
public final class InMemoryIdentityRateLimiter implements IdentityRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final int MAXIMUM_ATTEMPTS_PER_WINDOW = 20;
    private static final int MAXIMUM_KEYS = 10_000;

    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Override
    public boolean isAllowed(IdentityRateLimitAttempt attempt) {
        Objects.requireNonNull(attempt, "attempt");
        var key = keyFor(attempt);
        var now = attempt.attemptedAt();
        counters.compute(key, (ignored, existing) -> nextCounter(existing, now));
        if (counters.size() > MAXIMUM_KEYS) {
            evictExpired(now);
        }
        var counter = counters.get(key);
        return counter != null && counter.count() <= MAXIMUM_ATTEMPTS_PER_WINDOW;
    }

    private static String keyFor(IdentityRateLimitAttempt attempt) {
        return attempt.action()
                + "|"
                + attempt.accountKey()
                + "|"
                + attempt.requestContext().clientAddress();
    }

    private static WindowCounter nextCounter(WindowCounter existing, Instant now) {
        if (existing == null || !existing.windowStartedAt().plus(WINDOW).isAfter(now)) {
            return new WindowCounter(now, 1);
        }
        return new WindowCounter(existing.windowStartedAt(), existing.count() + 1);
    }

    private void evictExpired(Instant now) {
        for (Map.Entry<String, WindowCounter> entry : counters.entrySet()) {
            if (!entry.getValue().windowStartedAt().plus(WINDOW).isAfter(now)) {
                counters.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private record WindowCounter(Instant windowStartedAt, int count) {}
}
