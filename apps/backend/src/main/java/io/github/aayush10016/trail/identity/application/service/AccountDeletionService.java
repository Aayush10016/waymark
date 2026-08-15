package io.github.aayush10016.trail.identity.application.service;

import io.github.aayush10016.trail.identity.application.command.DeleteAccountCommand;
import io.github.aayush10016.trail.identity.application.dto.AccountDeletionResponse;
import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.identity.domain.port.AccountDeletionRepository;
import io.github.aayush10016.trail.identity.domain.port.PasswordVerificationPolicy;
import java.time.Clock;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for authenticated account deletion.
 */
public class AccountDeletionService {

    private final AccountDeletionRepository accountDeletionRepository;
    private final PasswordVerificationPolicy passwordVerificationPolicy;
    private final Clock clock;

    /**
     * Creates the service.
     */
    public AccountDeletionService(
            AccountDeletionRepository accountDeletionRepository,
            PasswordVerificationPolicy passwordVerificationPolicy,
            Clock clock) {
        this.accountDeletionRepository = Objects.requireNonNull(accountDeletionRepository, "accountDeletionRepository");
        this.passwordVerificationPolicy =
                Objects.requireNonNull(passwordVerificationPolicy, "passwordVerificationPolicy");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Reauthenticates, revokes refresh sessions, and purges all user-owned active data.
     */
    @Transactional
    public AccountDeletionResponse delete(AuthenticatedPrincipal principal, DeleteAccountCommand command) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(command, "command");

        if (!passwordVerificationPolicy.isWithinMaximumInputSize(command.password())) {
            throw new AccountDeletionReauthenticationException();
        }
        var passwordHash = accountDeletionRepository
                .findActivePasswordHash(principal)
                .orElseThrow(AccountDeletionReauthenticationException::new);
        if (!passwordVerificationPolicy.matches(command.password(), passwordHash)) {
            throw new AccountDeletionReauthenticationException();
        }

        var deletedAt = clock.instant();
        accountDeletionRepository.revokeRefreshSessions(principal, deletedAt);
        if (!accountDeletionRepository.purgeAccount(principal)) {
            throw new AccountDeletionReauthenticationException();
        }
        return AccountDeletionResponse.completed(deletedAt);
    }
}
