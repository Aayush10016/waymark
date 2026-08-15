package io.github.aayush10016.trail.capture.domain.service;

import io.github.aayush10016.trail.capture.domain.model.AllowedCaptureSanitization;
import io.github.aayush10016.trail.capture.domain.model.CaptureRejectionReason;
import io.github.aayush10016.trail.capture.domain.model.CaptureSanitizationPolicy;
import io.github.aayush10016.trail.capture.domain.model.CaptureSanitizationResult;
import io.github.aayush10016.trail.capture.domain.model.RawCaptureCandidate;
import io.github.aayush10016.trail.capture.domain.model.RejectedCaptureSanitization;
import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Applies Trail backend URL, host, query, and title sanitation before any capture data may be ingested.
 */
public final class UrlQuerySanitizer {

    private static final int TITLE_MAX_LENGTH = 120;
    private static final int SEARCH_QUERY_MAX_LENGTH = 240;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Set<String> TRACKING_QUERY_PARAMETERS = Set.of("fbclid", "gclid", "msclkid");
    private static final Set<String> SENSITIVE_QUERY_PARAMETERS =
            Set.of(
                    "access_token",
                    "api_key",
                    "auth",
                    "authorization",
                    "code",
                    "id_token",
                    "key",
                    "password",
                    "refresh_token",
                    "secret",
                    "state",
                    "token");
    private static final Set<String> NON_ALLOWLISTED_SEARCH_QUERY_PARAMETERS = Set.of("q", "query", "search");

    private final CaptureSanitizationPolicy policy;

    /**
     * Creates a sanitizer for the provided policy.
     *
     * @param policy versioned sanitation policy
     */
    public UrlQuerySanitizer(CaptureSanitizationPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /**
     * Sanitizes a raw capture candidate.
     *
     * @param input raw candidate
     * @return allowed sanitized metadata or a stable rejection result
     */
    public CaptureSanitizationResult sanitize(RawCaptureCandidate input) {
        Objects.requireNonNull(input, "input");

        var parsedUri = parseUri(input.url());
        if (parsedUri == null || !hasSupportedScheme(parsedUri)) {
            return reject(CaptureRejectionReason.UNSUPPORTED_SCHEME);
        }

        var authority = parsedUri.getRawAuthority();
        if (authority == null || authority.isBlank()) {
            return reject(CaptureRejectionReason.UNSUPPORTED_SCHEME);
        }
        if (authority.contains("@")) {
            return reject(CaptureRejectionReason.CREDENTIALS_PRESENT);
        }

        var hostname = extractHostname(authority);
        if (hostname == null || hostname.isBlank()) {
            return reject(CaptureRejectionReason.UNSUPPORTED_SCHEME);
        }
        if (isPrivateOrLocalHost(hostname)) {
            return reject(CaptureRejectionReason.PRIVATE_OR_LOCAL_HOST);
        }

        var searchMatch = findSearchMatch(hostname, parsedUri.getRawPath());
        var querySanitation = sanitizeQuery(parsedUri.getRawQuery(), searchMatch);

        return new AllowedCaptureSanitization(
                buildCanonicalUrl(parsedUri, hostname, querySanitation.retainedParameters()),
                hostname,
                policy.policyVersion(),
                querySanitation.removedParameters(),
                searchMatch == null ? null : searchMatch.engine(),
                querySanitation.searchQuery(),
                sanitizeBoundedText(input.title(), TITLE_MAX_LENGTH));
    }

    private URI parseUri(String rawUrl) {
        try {
            return new URI(rawUrl);
        } catch (URISyntaxException | IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean hasSupportedScheme(URI parsedUri) {
        var scheme = parsedUri.getScheme();
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    private RejectedCaptureSanitization reject(CaptureRejectionReason reason) {
        return new RejectedCaptureSanitization(policy.policyVersion(), reason);
    }

    private String extractHostname(String rawAuthority) {
        var authority = rawAuthority.toLowerCase(Locale.ROOT);
        if (authority.startsWith("[")) {
            var closingBracketIndex = authority.indexOf(']');
            return closingBracketIndex < 0 ? null : authority.substring(1, closingBracketIndex);
        }

        var host = authority;
        var colonIndex = host.lastIndexOf(':');
        if (colonIndex >= 0 && host.indexOf(':') == colonIndex) {
            host = host.substring(0, colonIndex);
        }

        try {
            return IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean isPrivateOrLocalHost(String hostname) {
        var normalizedHostname = hostname.toLowerCase(Locale.ROOT);
        if (normalizedHostname.equals("localhost")
                || normalizedHostname.endsWith(".localhost")
                || normalizedHostname.equals("0.0.0.0")
                || normalizedHostname.equals("::")
                || normalizedHostname.equals("::1")) {
            return true;
        }

        if (isIpv4Address(normalizedHostname)) {
            return isPrivateIpv4Address(normalizedHostname);
        }

        if (normalizedHostname.contains(":")) {
            return isPrivateIpv6Address(normalizedHostname);
        }

        return false;
    }

    private SearchMatch findSearchMatch(String hostname, String rawPath) {
        var path = rawPath == null || rawPath.isBlank() ? "/" : rawPath;
        for (var entry : policy.searchAllowlist()) {
            var hostMatches = entry.hosts().stream().anyMatch(host -> host.equalsIgnoreCase(hostname));
            var pathMatches = entry.pathPrefixes().stream().anyMatch(path::startsWith);
            if (hostMatches && pathMatches) {
                return new SearchMatch(entry.engine(), entry.queryParameters());
            }
        }
        return null;
    }

    private QuerySanitation sanitizeQuery(String rawQuery, SearchMatch searchMatch) {
        var removedParameters = new ArrayList<String>();
        var retainedParameters = new ArrayList<QueryParameter>();
        String searchQuery = null;

        for (var parameter : parseQuery(rawQuery)) {
            var normalizedName = parameter.name().toLowerCase(Locale.ROOT);
            var allowlistedSearchParameter =
                    searchMatch != null && searchMatch.queryParameters().contains(parameter.name());

            if (shouldRemoveQueryParameter(normalizedName, searchMatch, allowlistedSearchParameter)) {
                removedParameters.add(parameter.name());
                continue;
            }

            retainedParameters.add(parameter);
            if (searchQuery == null && searchMatch != null && allowlistedSearchParameter) {
                searchQuery = sanitizeBoundedText(parameter.value(), SEARCH_QUERY_MAX_LENGTH);
            }
        }

        return new QuerySanitation(List.copyOf(removedParameters), List.copyOf(retainedParameters), searchQuery);
    }

    private List<QueryParameter> parseQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return List.of();
        }

        var parameters = new ArrayList<QueryParameter>();
        for (var rawPair : rawQuery.split("&", -1)) {
            var equalsIndex = rawPair.indexOf('=');
            var rawName = equalsIndex < 0 ? rawPair : rawPair.substring(0, equalsIndex);
            var rawValue = equalsIndex < 0 ? "" : rawPair.substring(equalsIndex + 1);
            parameters.add(new QueryParameter(percentDecode(rawName), percentDecode(rawValue)));
        }
        return List.copyOf(parameters);
    }

    private boolean shouldRemoveQueryParameter(
            String normalizedName, SearchMatch searchMatch, boolean allowlistedSearchParameter) {
        if (normalizedName.startsWith("utm_")) {
            return true;
        }
        if (TRACKING_QUERY_PARAMETERS.contains(normalizedName) || SENSITIVE_QUERY_PARAMETERS.contains(normalizedName)) {
            return true;
        }
        if (searchMatch != null) {
            return !allowlistedSearchParameter;
        }
        return NON_ALLOWLISTED_SEARCH_QUERY_PARAMETERS.contains(normalizedName);
    }

    private String buildCanonicalUrl(URI parsedUri, String hostname, List<QueryParameter> retainedParameters) {
        var rawPath = parsedUri.getRawPath();
        var path = rawPath == null || rawPath.isBlank() ? "/" : rawPath;
        var port = parsedUri.getPort();
        var query = retainedParameters.stream()
                .map(parameter -> percentEncode(parameter.name()) + "=" + percentEncode(parameter.value()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");

        return parsedUri.getScheme().toLowerCase(Locale.ROOT)
                + "://"
                + hostname
                + (port < 0 ? "" : ":" + port)
                + path
                + (query.isBlank() ? "" : "?" + query);
    }

    private String sanitizeBoundedText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        var normalized = WHITESPACE.matcher(value).replaceAll(" ").strip();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private String percentDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String percentEncode(String value) {
        var encoded = new StringBuilder();
        for (var byteValue : value.getBytes(StandardCharsets.UTF_8)) {
            var unsignedByte = Byte.toUnsignedInt(byteValue);
            if (isUnreservedAscii(unsignedByte)) {
                encoded.append((char) unsignedByte);
            } else {
                encoded.append('%').append(HexFormat.of().toHexDigits(byteValue).toUpperCase(Locale.ROOT));
            }
        }
        return encoded.toString();
    }

    private boolean isUnreservedAscii(int value) {
        return (value >= 'A' && value <= 'Z')
                || (value >= 'a' && value <= 'z')
                || (value >= '0' && value <= '9')
                || value == '-'
                || value == '_'
                || value == '.'
                || value == '~';
    }

    private boolean isIpv4Address(String value) {
        var octets = value.split("\\.");
        if (octets.length != 4) {
            return false;
        }
        for (var octet : octets) {
            if (!octet.matches("\\d{1,3}")) {
                return false;
            }
            var numericOctet = Integer.parseInt(octet);
            if (numericOctet < 0 || numericOctet > 255) {
                return false;
            }
        }
        return true;
    }

    private boolean isPrivateIpv4Address(String value) {
        var octets = value.split("\\.");
        var firstOctet = Integer.parseInt(octets[0]);
        var secondOctet = Integer.parseInt(octets[1]);

        return firstOctet == 0
                || firstOctet == 10
                || firstOctet == 127
                || (firstOctet == 100 && secondOctet >= 64 && secondOctet <= 127)
                || (firstOctet == 169 && secondOctet == 254)
                || (firstOctet == 172 && secondOctet >= 16 && secondOctet <= 31)
                || (firstOctet == 192 && secondOctet == 168);
    }

    private boolean isPrivateIpv6Address(String value) {
        return value.equals("::")
                || value.equals("::1")
                || value.startsWith("fc")
                || value.startsWith("fd")
                || value.startsWith("fe8")
                || value.startsWith("fe9")
                || value.startsWith("fea")
                || value.startsWith("feb");
    }

    private record SearchMatch(String engine, List<String> queryParameters) {}

    private record QueryParameter(String name, String value) {}

    private record QuerySanitation(
            List<String> removedParameters, List<QueryParameter> retainedParameters, String searchQuery) {}
}
