package io.github.aayush10016.trail.identity.application.service;

import io.github.aayush10016.trail.identity.domain.model.IdentityRateLimitAttempt;
import io.github.aayush10016.trail.identity.domain.port.IdentityRateLimiter;
import java.util.ArrayList;
import java.util.List;

final class IdentityRateLimiterTestDoubles {

    private IdentityRateLimiterTestDoubles() {}

    static CapturingIdentityRateLimiter allowingRateLimiter() {
        return new CapturingIdentityRateLimiter(true);
    }

    static CapturingIdentityRateLimiter denyingRateLimiter() {
        return new CapturingIdentityRateLimiter(false);
    }

    static final class CapturingIdentityRateLimiter implements IdentityRateLimiter {

        private final boolean allowed;
        private final List<IdentityRateLimitAttempt> attempts = new ArrayList<>();

        private CapturingIdentityRateLimiter(boolean allowed) {
            this.allowed = allowed;
        }

        @Override
        public boolean isAllowed(IdentityRateLimitAttempt attempt) {
            attempts.add(attempt);
            return allowed;
        }

        List<IdentityRateLimitAttempt> attempts() {
            return List.copyOf(attempts);
        }
    }
}
