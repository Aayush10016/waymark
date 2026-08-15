package io.github.aayush10016.trail.capture.domain.model;

import java.util.List;

/**
 * Sanitized capture metadata safe to pass to later ingestion code.
 *
 * @param canonicalUrl canonical sanitized URL without fragments or stripped parameters
 * @param hostname canonical hostname
 * @param policyVersion sanitation policy version
 * @param removedQueryParameters query parameter names removed during sanitation
 * @param searchEngine allowlisted search engine identifier when a query was extracted
 * @param searchQuery allowlisted and bounded search query when present
 * @param title bounded title when present
 */
public record AllowedCaptureSanitization(
        String canonicalUrl,
        String hostname,
        String policyVersion,
        List<String> removedQueryParameters,
        String searchEngine,
        String searchQuery,
        String title)
        implements CaptureSanitizationResult {

    /**
     * Creates immutable sanitized metadata.
     */
    public AllowedCaptureSanitization {
        if (canonicalUrl == null || canonicalUrl.isBlank()) {
            throw new IllegalArgumentException("canonicalUrl must not be blank");
        }
        if (hostname == null || hostname.isBlank()) {
            throw new IllegalArgumentException("hostname must not be blank");
        }
        if (policyVersion == null || policyVersion.isBlank()) {
            throw new IllegalArgumentException("policyVersion must not be blank");
        }
        removedQueryParameters = List.copyOf(removedQueryParameters);
    }
}
