package io.github.aayush10016.trail.capture.adapter.out.persistence;

import io.github.aayush10016.trail.capture.domain.port.CaptureRetentionRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL retention adapter for short-lived raw capture events.
 */
@Repository
public class JdbcCaptureRetentionRepository implements CaptureRetentionRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Creates the repository.
     */
    public JdbcCaptureRetentionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public int expireRawCaptureEvents(Instant expiresAtOrBefore, int batchSize) {
        Objects.requireNonNull(expiresAtOrBefore, "expiresAtOrBefore");
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }

        return jdbcTemplate.update(
                connection -> {
                    var statement =
                            connection.prepareStatement(
                                    """
                                    with expired_capture_events as (
                                        select id
                                        from capture_events
                                        where retention_expires_at <= ?
                                        order by retention_expires_at, id
                                        limit ?
                                        for update skip locked
                                    )
                                    delete from capture_events
                                    using expired_capture_events
                                    where capture_events.id = expired_capture_events.id
                                    """);
                    statement.setTimestamp(1, Timestamp.from(expiresAtOrBefore));
                    statement.setInt(2, batchSize);
                    return statement;
                });
    }
}
