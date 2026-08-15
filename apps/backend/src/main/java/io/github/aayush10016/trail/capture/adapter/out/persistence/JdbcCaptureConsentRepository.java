package io.github.aayush10016.trail.capture.adapter.out.persistence;

import io.github.aayush10016.trail.capture.domain.port.CaptureConsentRepository;
import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL consent evidence reader for capture ingestion.
 */
@Repository
public class JdbcCaptureConsentRepository implements CaptureConsentRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Creates the repository.
     */
    public JdbcCaptureConsentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public boolean hasActiveCaptureConsent(AuthenticatedPrincipal principal) {
        Objects.requireNonNull(principal, "principal");
        try {
            var decision = jdbcTemplate.queryForObject(
                    """
                    select decision
                    from consent_grants
                    where user_id = ?
                        and capture_scope = 'BROWSER_NAVIGATION_METADATA'
                    order by created_at desc, id desc
                    limit 1
                    """,
                    String.class,
                    principal.userId());
            return "GRANTED".equals(decision);
        } catch (EmptyResultDataAccessException exception) {
            return false;
        }
    }

    @Override
    public Optional<String> latestCapturePolicyVersion(AuthenticatedPrincipal principal) {
        Objects.requireNonNull(principal, "principal");
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    """
                    select policy_version
                    from consent_grants
                    where user_id = ?
                        and capture_scope = 'BROWSER_NAVIGATION_METADATA'
                    order by created_at desc, id desc
                    limit 1
                    """,
                    String.class,
                    principal.userId()));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public void recordGrantedCaptureConsent(AuthenticatedPrincipal principal, String policyVersion, Instant grantedAt) {
        insertConsentDecision(principal, policyVersion, "GRANTED", grantedAt, null);
    }

    @Override
    public void recordWithdrawnCaptureConsent(AuthenticatedPrincipal principal, String policyVersion, Instant withdrawnAt) {
        insertConsentDecision(principal, policyVersion, "WITHDRAWN", null, withdrawnAt);
    }

    private void insertConsentDecision(
            AuthenticatedPrincipal principal,
            String policyVersion,
            String decision,
            Instant grantedAt,
            Instant withdrawnAt) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(policyVersion, "policyVersion");
        Objects.requireNonNull(decision, "decision");

        jdbcTemplate.update(
                """
                insert into consent_grants (
                    id,
                    user_id,
                    policy_version,
                    capture_scope,
                    ai_scope,
                    decision,
                    granted_at,
                    withdrawn_at
                ) values (?, ?, ?, 'BROWSER_NAVIGATION_METADATA', 'NONE', ?, ?, ?)
                """,
                UUID.randomUUID(),
                principal.userId(),
                policyVersion,
                decision,
                nullableTimestamp(grantedAt),
                nullableTimestamp(withdrawnAt));
    }

    private static Timestamp nullableTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
