package io.github.aayush10016.trail.capture.domain.model;

/**
 * Backend URL/query sanitation result.
 */
public sealed interface CaptureSanitizationResult
        permits AllowedCaptureSanitization, RejectedCaptureSanitization {

    /**
     * Returns the policy version that produced this result.
     */
    String policyVersion();
}
