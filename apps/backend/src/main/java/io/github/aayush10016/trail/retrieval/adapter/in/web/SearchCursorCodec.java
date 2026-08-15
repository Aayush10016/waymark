package io.github.aayush10016.trail.retrieval.adapter.in.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Encodes and validates opaque search cursors pinned to a normalized filter fingerprint.
 */
final class SearchCursorCodec {

    private static final String CURSOR_VERSION = "v1";
    private static final String DELIMITER = ":";
    private static final int OFFSET_INDEX = 1;
    private static final int FINGERPRINT_INDEX = 2;

    private SearchCursorCodec() {}

    static String fingerprint(
            String query,
            List<String> technologies,
            List<String> errorPhrases,
            List<String> problemTypes) {
        var canonical = String.join(
                "\n",
                "query=" + normalizeOptional(query),
                "technologies=" + canonicalList(technologies),
                "errorPhrases=" + canonicalList(errorPhrases),
                "problemTypes=" + canonicalList(problemTypes));
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    static String encode(String fingerprint, int offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative");
        }
        var payload = CURSOR_VERSION + DELIMITER + offset + DELIMITER + requireFingerprint(fingerprint);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    static int decodeOffset(String cursor, String expectedFingerprint) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }

        var decoded = decodePayload(cursor);
        var parts = decoded.split(DELIMITER, -1);
        if (parts.length != 3 || !CURSOR_VERSION.equals(parts[0])) {
            throw RetrievalApiException.badRequest("SEARCH_INVALID_CURSOR", "Search cursor is invalid.");
        }
        if (!requireFingerprint(expectedFingerprint).equals(parts[FINGERPRINT_INDEX])) {
            throw RetrievalApiException.badRequest(
                    "SEARCH_CURSOR_FILTER_MISMATCH", "Search cursor does not match the current filters.");
        }
        try {
            var offset = Integer.parseInt(parts[OFFSET_INDEX]);
            if (offset < 0) {
                throw RetrievalApiException.badRequest("SEARCH_INVALID_CURSOR", "Search cursor is invalid.");
            }
            return offset;
        } catch (NumberFormatException exception) {
            throw RetrievalApiException.badRequest("SEARCH_INVALID_CURSOR", "Search cursor is invalid.");
        }
    }

    private static String decodePayload(String cursor) {
        try {
            return new String(Base64.getUrlDecoder().decode(cursor.strip()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw RetrievalApiException.badRequest("SEARCH_INVALID_CURSOR", "Search cursor is invalid.");
        }
    }

    private static String requireFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            throw new IllegalArgumentException("fingerprint must not be blank");
        }
        return fingerprint.strip();
    }

    private static String canonicalList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(SearchCursorCodec::normalizeOptional)
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted(Comparator.naturalOrder())
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Stream.of(value.strip().toLowerCase(java.util.Locale.ROOT).split("\\s+"))
                .filter(token -> !token.isBlank())
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }
}
