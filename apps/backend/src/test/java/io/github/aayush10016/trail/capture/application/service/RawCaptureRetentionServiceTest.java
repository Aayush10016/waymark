package io.github.aayush10016.trail.capture.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.aayush10016.trail.capture.domain.port.CaptureRetentionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RawCaptureRetentionServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-20T08:00:00Z");

    @Test
    void expiresRawCaptureEventsInBoundedChunks() {
        var repository = new FakeCaptureRetentionRepository(2, 2, 1, 0);
        var service = new RawCaptureRetentionService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

        var result = service.expireExpiredRawCaptureEvents(2, 5);

        assertThat(result.expiredEventCount()).isEqualTo(5);
        assertThat(result.batchCount()).isEqualTo(3);
        assertThat(result.complete()).isTrue();
        assertThat(repository.calls()).containsExactly(
                new RetentionCall(NOW, 2),
                new RetentionCall(NOW, 2),
                new RetentionCall(NOW, 2));
    }

    @Test
    void stopsWhenJobBudgetIsExhausted() {
        var repository = new FakeCaptureRetentionRepository(2, 2, 2);
        var service = new RawCaptureRetentionService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

        var result = service.expireExpiredRawCaptureEvents(2, 2);

        assertThat(result.expiredEventCount()).isEqualTo(4);
        assertThat(result.batchCount()).isEqualTo(2);
        assertThat(result.complete()).isFalse();
    }

    @Test
    void isIdempotentWhenNothingIsExpired() {
        var repository = new FakeCaptureRetentionRepository(0);
        var service = new RawCaptureRetentionService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

        var result = service.expireExpiredRawCaptureEvents(100, 10);

        assertThat(result.expiredEventCount()).isZero();
        assertThat(result.batchCount()).isZero();
        assertThat(result.complete()).isTrue();
        assertThat(repository.calls()).containsExactly(new RetentionCall(NOW, 100));
    }

    @Test
    void rejectsInvalidChunkConfiguration() {
        var service = new RawCaptureRetentionService(new FakeCaptureRetentionRepository(), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.expireExpiredRawCaptureEvents(0, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batchSize");
        assertThatThrownBy(() -> service.expireExpiredRawCaptureEvents(1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxBatches");
    }

    private record RetentionCall(Instant cutoff, int batchSize) {}

    private static final class FakeCaptureRetentionRepository implements CaptureRetentionRepository {
        private final List<RetentionCall> calls = new ArrayList<>();
        private final ArrayDeque<Integer> results;

        private FakeCaptureRetentionRepository(Integer... results) {
            this.results = new ArrayDeque<>(List.of(results));
        }

        @Override
        public int expireRawCaptureEvents(Instant expiresAtOrBefore, int batchSize) {
            calls.add(new RetentionCall(expiresAtOrBefore, batchSize));
            return results.isEmpty() ? 0 : results.removeFirst();
        }

        private List<RetentionCall> calls() {
            return List.copyOf(calls);
        }
    }
}
