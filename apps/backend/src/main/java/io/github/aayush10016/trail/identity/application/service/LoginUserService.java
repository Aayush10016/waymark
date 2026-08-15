package io.github.aayush10016.trail.identity.application.service;

import io.github.aayush10016.trail.identity.application.command.LoginCommand;
import io.github.aayush10016.trail.identity.application.dto.LoginResult;
import io.github.aayush10016.trail.identity.domain.model.EmailAddress;
import io.github.aayush10016.trail.identity.domain.model.IdentityRateLimitAction;
import io.github.aayush10016.trail.identity.domain.model.IdentityRateLimitAttempt;
import io.github.aayush10016.trail.identity.domain.model.LoginAccount;
import io.github.aayush10016.trail.identity.domain.model.UserStatus;
import io.github.aayush10016.trail.identity.domain.port.AccessTokenIssuer;
import io.github.aayush10016.trail.identity.domain.port.IdentityRateLimiter;
import io.github.aayush10016.trail.identity.domain.port.LoginUserRepository;
import io.github.aayush10016.trail.identity.domain.port.PasswordVerificationPolicy;
import java.time.Clock;
import java.util.Objects;

/**
 * Application use case for email/password login and credential issuance.
 */
public final class LoginUserService {

    private final LoginUserRepository loginUserRepository;
    private final PasswordVerificationPolicy passwordVerificationPolicy;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshSessionCreator refreshSessionCreator;
    private final IdentityRateLimiter rateLimiter;
    private final Clock clock;

    /**
     * Creates the login use case.
     *
     * @param loginUserRepository login account lookup port
     * @param passwordVerificationPolicy password verification policy
     * @param accessTokenIssuer access-token issuer
     * @param refreshSessionCreator refresh-session creator
     * @param rateLimiter identity rate-limit port
     * @param clock application clock
     */
    public LoginUserService(
            LoginUserRepository loginUserRepository,
            PasswordVerificationPolicy passwordVerificationPolicy,
            AccessTokenIssuer accessTokenIssuer,
            RefreshSessionCreator refreshSessionCreator,
            IdentityRateLimiter rateLimiter,
            Clock clock) {
        this.loginUserRepository = Objects.requireNonNull(loginUserRepository, "loginUserRepository");
        this.passwordVerificationPolicy =
                Objects.requireNonNull(passwordVerificationPolicy, "passwordVerificationPolicy");
        this.accessTokenIssuer = Objects.requireNonNull(accessTokenIssuer, "accessTokenIssuer");
        this.refreshSessionCreator = Objects.requireNonNull(refreshSessionCreator, "refreshSessionCreator");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Authenticates a verified account and issues access and refresh credentials.
     *
     * @param command login command
     * @return authenticated result with access token or a generic rejected result
     */
    public LoginResult login(LoginCommand command) {
        Objects.requireNonNull(command, "command");
        if (!passwordVerificationPolicy.isWithinMaximumInputSize(command.password())) {
            return LoginResult.rejected();
        }

        var email = EmailAddress.from(command.email());
        var attempt =
                IdentityRateLimitAttempt.forEmail(
                        IdentityRateLimitAction.LOGIN,
                        email,
                        command.requestContext(),
                        clock.instant());
        if (!rateLimiter.isAllowed(attempt)) {
            return LoginResult.rejected();
        }

        var account = loginUserRepository.findByEmail(email);
        if (account.isEmpty()) {
            return LoginResult.rejected();
        }

        var loginAccount = account.get();
        if (!passwordVerificationPolicy.matches(command.password(), loginAccount.passwordHash())) {
            return LoginResult.rejected();
        }

        if (!isEligibleForLogin(loginAccount)) {
            return LoginResult.rejected();
        }

        if (passwordVerificationPolicy.requiresRehash(loginAccount.passwordHash())) {
            loginUserRepository.updatePasswordHash(
                    loginAccount.id(), passwordVerificationPolicy.hash(command.password()));
        }

        var principal = loginAccount.toPrincipal();
        return LoginResult.authenticated(
                accessTokenIssuer.issue(principal), refreshSessionCreator.createSession(principal));
    }

    private static boolean isEligibleForLogin(LoginAccount account) {
        return account.status() == UserStatus.ACTIVE && account.emailVerified();
    }
}
