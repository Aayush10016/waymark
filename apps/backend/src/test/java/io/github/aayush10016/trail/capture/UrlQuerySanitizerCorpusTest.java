package io.github.aayush10016.trail.capture;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aayush10016.trail.capture.domain.model.AllowedCaptureSanitization;
import io.github.aayush10016.trail.capture.domain.model.CaptureSanitizationPolicy;
import io.github.aayush10016.trail.capture.domain.model.CaptureSearchAllowlistEntry;
import io.github.aayush10016.trail.capture.domain.model.RawCaptureCandidate;
import io.github.aayush10016.trail.capture.domain.model.RejectedCaptureSanitization;
import io.github.aayush10016.trail.capture.domain.service.UrlQuerySanitizer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class UrlQuerySanitizerCorpusTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final UrlQuerySanitationCorpus CORPUS = loadCorpus();

    private final UrlQuerySanitizer sanitizer =
            new UrlQuerySanitizer(
                    new CaptureSanitizationPolicy(CORPUS.policyVersion(), CORPUS.searchAllowlist()));

    @Test
    void matchesSharedEng026FixtureCorpusAndIsNotMorePermissiveThanExtension() {
        for (var fixture : CORPUS.cases()) {
            var result = sanitizer.sanitize(new RawCaptureCandidate(fixture.input().url(), fixture.input().title()));

            assertThat(result.policyVersion()).as(fixture.id()).isEqualTo(fixture.expected().policyVersion());

            if ("reject".equals(fixture.expected().decision())) {
                assertThat(result).as(fixture.id()).isInstanceOf(RejectedCaptureSanitization.class);
                var rejected = (RejectedCaptureSanitization) result;
                assertThat(rejected.rejectionReason().wireValue())
                        .as(fixture.id())
                        .isEqualTo(fixture.expected().rejectionReason());
                continue;
            }

            assertThat(result).as(fixture.id()).isInstanceOf(AllowedCaptureSanitization.class);
            var allowed = (AllowedCaptureSanitization) result;
            assertThat(allowed.canonicalUrl()).as(fixture.id()).isEqualTo(fixture.expected().canonicalUrl());
            assertThat(allowed.hostname()).as(fixture.id()).isEqualTo(fixture.expected().hostname());
            assertThat(allowed.searchEngine()).as(fixture.id()).isEqualTo(fixture.expected().searchEngine());
            assertThat(allowed.searchQuery()).as(fixture.id()).isEqualTo(fixture.expected().searchQuery());
            assertThat(allowed.removedQueryParameters())
                    .as(fixture.id())
                    .containsExactlyElementsOf(fixture.expected().removedQueryParameters());

            if (fixture.expected().titleMaxLength() != null) {
                assertThat(allowed.title()).as(fixture.id()).hasSizeLessThanOrEqualTo(fixture.expected().titleMaxLength());
            }
        }
    }

    private static UrlQuerySanitationCorpus loadCorpus() {
        try {
            return OBJECT_MAPPER.readValue(
                    Path.of(
                                    "..",
                                    "..",
                                    "contracts",
                                    "fixtures",
                                    "url-query-sanitation",
                                    "v1",
                                    "corpus.json")
                            .toFile(),
                    UrlQuerySanitationCorpus.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load ENG-026 URL/query sanitation corpus.", exception);
        }
    }

    private record UrlQuerySanitationCorpus(
            int corpusVersion,
            String policyVersion,
            String description,
            List<CaptureSearchAllowlistEntry> searchAllowlist,
            List<UrlQuerySanitationFixture> cases) {}

    private record UrlQuerySanitationFixture(
            String id,
            List<String> categories,
            UrlQuerySanitationInput input,
            UrlQuerySanitationExpectedDecision expected) {}

    private record UrlQuerySanitationInput(String url, String title) {}

    private record UrlQuerySanitationExpectedDecision(
            String decision,
            String canonicalUrl,
            String hostname,
            String searchEngine,
            String searchQuery,
            List<String> removedQueryParameters,
            Integer titleMaxLength,
            String rejectionReason,
            String policyVersion) {}
}
