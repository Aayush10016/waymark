package io.github.aayush10016.trail.capture.domain.model;

import java.util.List;

/**
 * Versioned backend URL/query sanitation policy.
 *
 * @param policyVersion stable policy version recorded on every decision
 * @param searchAllowlist search engines eligible for query extraction
 */
public record CaptureSanitizationPolicy(String policyVersion, List<CaptureSearchAllowlistEntry> searchAllowlist) {

    /**
     * Creates an immutable sanitation policy.
     */
    public CaptureSanitizationPolicy {
        if (policyVersion == null || policyVersion.isBlank()) {
            throw new IllegalArgumentException("policyVersion must not be blank");
        }
        searchAllowlist = List.copyOf(searchAllowlist);
    }
}
