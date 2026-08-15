package io.github.aayush10016.trail.capture.domain.model;

import java.util.List;

/**
 * Versioned search-engine allowlist entry used by URL/query sanitation.
 *
 * @param engine stable search engine identifier
 * @param hosts exact hostnames allowed for query extraction
 * @param pathPrefixes allowed path prefixes for query extraction
 * @param queryParameters query parameter names that may be retained and extracted
 */
public record CaptureSearchAllowlistEntry(
        String engine, List<String> hosts, List<String> pathPrefixes, List<String> queryParameters) {

    /**
     * Creates an immutable allowlist entry.
     */
    public CaptureSearchAllowlistEntry {
        if (engine == null || engine.isBlank()) {
            throw new IllegalArgumentException("engine must not be blank");
        }
        hosts = List.copyOf(hosts);
        pathPrefixes = List.copyOf(pathPrefixes);
        queryParameters = List.copyOf(queryParameters);
    }
}
