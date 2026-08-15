package io.github.aayush10016.trail.capture.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aayush10016.trail.capture.application.dto.AcceptedCaptureIngestion;
import io.github.aayush10016.trail.capture.application.dto.CaptureIngestionRejectionCode;
import io.github.aayush10016.trail.capture.application.dto.RejectedCaptureIngestion;
import io.github.aayush10016.trail.capture.domain.model.CaptureSanitizationPolicy;
import io.github.aayush10016.trail.capture.domain.service.UrlQuerySanitizer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CaptureIngestionValidatorTest {

    private final CaptureIngestionValidator validator =
            new CaptureIngestionValidator(new UrlQuerySanitizer(new CaptureSanitizationPolicy("test-policy", List.of())));

    @Test
    void acceptsSanitizedCaptureInputOnlyAfterBackendSanitation() {
        var result =
                validator.validate(
                        Map.of(
                                "url",
                                "https://example.test/docs?utm_source=fixture&topic=cors#fragment",
                                "title",
                                "Example docs"));

        assertThat(result).isInstanceOf(AcceptedCaptureIngestion.class);
        var accepted = (AcceptedCaptureIngestion) result;
        assertThat(accepted.sanitizedEvent().canonicalUrl()).isEqualTo("https://example.test/docs?topic=cors");
        assertThat(accepted.sanitizedEvent().policyVersion()).isEqualTo("test-policy");
        assertThat(accepted.sanitizedEvent().removedQueryParameters()).containsExactly("utm_source");
    }

    @Test
    void rejectsUnknownFieldsBeforeSanitation() {
        var result =
                validator.validate(
                        Map.of(
                                "url",
                                "https://example.test/docs",
                                "title",
                                "Example docs",
                                "rawHtml",
                                "<main>must not be accepted</main>"));

        assertRejected(result, CaptureIngestionRejectionCode.UNKNOWN_FIELD);
    }

    @Test
    void rejectsMissingOrInvalidUrlFields() {
        assertRejected(validator.validate(Map.of("title", "Example docs")), CaptureIngestionRejectionCode.MISSING_URL);
        assertRejected(validator.validate(Map.of("url", " ")), CaptureIngestionRejectionCode.MISSING_URL);
        assertRejected(validator.validate(Map.of("url", 42)), CaptureIngestionRejectionCode.INVALID_FIELD_TYPE);
        assertRejected(
                validator.validate(Map.of("url", "https://example.test/docs", "title", 42)),
                CaptureIngestionRejectionCode.INVALID_FIELD_TYPE);
    }

    @Test
    void rejectsOversizeFieldsBeforeSanitation() {
        assertRejected(
                validator.validate(Map.of("url", "https://example.test/" + "a".repeat(2049))),
                CaptureIngestionRejectionCode.URL_TOO_LARGE);
        assertRejected(
                validator.validate(Map.of("url", "https://example.test/docs", "title", "a".repeat(4097))),
                CaptureIngestionRejectionCode.TITLE_TOO_LARGE);
    }

    @Test
    void rejectsUnsafeUrlsWithoutProducingSanitizedMetadata() {
        var result = validator.validate(Map.of("url", "https://user:password@example.test/private", "title", "Private"));

        assertThat(result).isInstanceOf(RejectedCaptureIngestion.class);
        var rejected = (RejectedCaptureIngestion) result;
        assertThat(rejected.code()).isEqualTo(CaptureIngestionRejectionCode.UNSAFE_URL);
        assertThat(rejected.policyVersion()).isEqualTo("test-policy");
        assertThat(rejected.detail()).isEqualTo("credentials_present");
    }

    private void assertRejected(Object result, CaptureIngestionRejectionCode expectedCode) {
        assertThat(result).isInstanceOf(RejectedCaptureIngestion.class);
        assertThat(((RejectedCaptureIngestion) result).code()).isEqualTo(expectedCode);
    }
}
