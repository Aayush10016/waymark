package io.github.aayush10016.trail.capture.application.service;

import io.github.aayush10016.trail.capture.application.dto.AcceptedCaptureIngestion;
import io.github.aayush10016.trail.capture.application.dto.CaptureIngestionRejectionCode;
import io.github.aayush10016.trail.capture.application.dto.CaptureIngestionValidationResult;
import io.github.aayush10016.trail.capture.application.dto.RejectedCaptureIngestion;
import io.github.aayush10016.trail.capture.domain.model.AllowedCaptureSanitization;
import io.github.aayush10016.trail.capture.domain.model.RawCaptureCandidate;
import io.github.aayush10016.trail.capture.domain.model.RejectedCaptureSanitization;
import io.github.aayush10016.trail.capture.domain.service.UrlQuerySanitizer;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Validates backend capture input before future ingestion endpoints or persistence can consume it.
 */
public final class CaptureIngestionValidator {

    private static final int MAXIMUM_URL_LENGTH = 2048;
    private static final int MAXIMUM_TITLE_LENGTH = 4096;
    private static final Set<String> ACCEPTED_FIELDS = Set.of("url", "title");

    private final UrlQuerySanitizer sanitizer;

    /**
     * Creates the ingestion validator.
     *
     * @param sanitizer backend URL/query sanitizer
     */
    public CaptureIngestionValidator(UrlQuerySanitizer sanitizer) {
        this.sanitizer = Objects.requireNonNull(sanitizer, "sanitizer");
    }

    /**
     * Validates and sanitizes one raw capture input map.
     *
     * @param input raw capture input fields
     * @return accepted sanitized metadata or a safe rejection code
     */
    public CaptureIngestionValidationResult validate(Map<String, ?> input) {
        Objects.requireNonNull(input, "input");

        if (!ACCEPTED_FIELDS.containsAll(input.keySet())) {
            return reject(CaptureIngestionRejectionCode.UNKNOWN_FIELD);
        }

        var rawUrl = input.get("url");
        if (rawUrl == null) {
            return reject(CaptureIngestionRejectionCode.MISSING_URL);
        }
        if (!(rawUrl instanceof String url)) {
            return reject(CaptureIngestionRejectionCode.INVALID_FIELD_TYPE);
        }
        if (url.isBlank()) {
            return reject(CaptureIngestionRejectionCode.MISSING_URL);
        }
        if (url.length() > MAXIMUM_URL_LENGTH) {
            return reject(CaptureIngestionRejectionCode.URL_TOO_LARGE);
        }

        var rawTitle = input.get("title");
        if (rawTitle != null && !(rawTitle instanceof String)) {
            return reject(CaptureIngestionRejectionCode.INVALID_FIELD_TYPE);
        }
        var title = (String) rawTitle;
        if (title != null && title.length() > MAXIMUM_TITLE_LENGTH) {
            return reject(CaptureIngestionRejectionCode.TITLE_TOO_LARGE);
        }

        var sanitationResult = sanitizer.sanitize(new RawCaptureCandidate(url, title));
        if (sanitationResult instanceof AllowedCaptureSanitization allowed) {
            return new AcceptedCaptureIngestion(allowed);
        }
        var rejected = (RejectedCaptureSanitization) sanitationResult;
        return new RejectedCaptureIngestion(
                CaptureIngestionRejectionCode.UNSAFE_URL,
                rejected.policyVersion(),
                rejected.rejectionReason().wireValue());
    }

    private RejectedCaptureIngestion reject(CaptureIngestionRejectionCode code) {
        return new RejectedCaptureIngestion(code, null, null);
    }
}
