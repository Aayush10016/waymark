package io.github.aayush10016.trail.capture.adapter.out.persistence;

import io.github.aayush10016.trail.capture.domain.model.CurrentCaptureCandidate;
import io.github.aayush10016.trail.capture.domain.port.CaptureStateRepository;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL adapter for backend capture-state candidate summaries.
 */
@Repository
public class JdbcCaptureStateRepository implements CaptureStateRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Creates the repository.
     */
    public JdbcCaptureStateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public Optional<CurrentCaptureCandidate> findLatestCurrentCandidate(UUID userId) {
        Objects.requireNonNull(userId, "userId");
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    """
                    select id, status, title, started_at, updated_at
                    from problem_sessions
                    where user_id = ?
                        and status in ('CANDIDATE', 'REVIEWABLE')
                        and deleted_at is null
                    order by started_at desc, id desc
                    limit 1
                    """,
                    (resultSet, rowNumber) -> new CurrentCaptureCandidate(
                            resultSet.getObject("id", UUID.class),
                            resultSet.getString("status"),
                            resultSet.getString("title"),
                            resultSet.getObject("started_at", Timestamp.class).toInstant(),
                            resultSet.getObject("updated_at", Timestamp.class).toInstant()),
                    userId));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }
}
