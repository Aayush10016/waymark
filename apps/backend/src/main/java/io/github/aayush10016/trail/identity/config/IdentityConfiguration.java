package io.github.aayush10016.trail.identity.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aayush10016.trail.identity.application.service.AccountDeletionService;
import io.github.aayush10016.trail.identity.application.service.EmailVerificationService;
import io.github.aayush10016.trail.identity.application.service.IdentityEmailTemplateFactory;
import io.github.aayush10016.trail.identity.application.service.LoginUserService;
import io.github.aayush10016.trail.identity.application.service.PasswordResetRequestService;
import io.github.aayush10016.trail.identity.application.service.PasswordResetService;
import io.github.aayush10016.trail.identity.application.service.RefreshSessionService;
import io.github.aayush10016.trail.identity.application.service.RegisterUserService;
import io.github.aayush10016.trail.identity.adapter.out.email.BrevoEmailDeliveryAdapter;
import io.github.aayush10016.trail.identity.adapter.out.email.LocalEmailDeliveryAdapter;
import io.github.aayush10016.trail.identity.adapter.out.ratelimit.InMemoryIdentityRateLimiter;
import io.github.aayush10016.trail.identity.domain.port.AccountDeletionRepository;
import io.github.aayush10016.trail.identity.domain.port.AccessTokenIssuer;
import io.github.aayush10016.trail.identity.domain.port.EmailActionTokenHashingPolicy;
import io.github.aayush10016.trail.identity.domain.port.EmailDeliveryPort;
import io.github.aayush10016.trail.identity.domain.port.EmailVerificationTokenGenerator;
import io.github.aayush10016.trail.identity.domain.port.EmailVerificationTokenRepository;
import io.github.aayush10016.trail.identity.domain.port.IdentityRateLimiter;
import io.github.aayush10016.trail.identity.domain.port.LoginUserRepository;
import io.github.aayush10016.trail.identity.domain.port.PasswordResetTokenGenerator;
import io.github.aayush10016.trail.identity.domain.port.PasswordResetTokenRepository;
import io.github.aayush10016.trail.identity.domain.port.RefreshSessionRepository;
import io.github.aayush10016.trail.identity.domain.port.RefreshTokenGenerator;
import io.github.aayush10016.trail.identity.domain.port.RefreshTokenHashingPolicy;
import io.github.aayush10016.trail.identity.security.Argon2idPasswordPolicy;
import io.github.aayush10016.trail.identity.security.SecureEmailVerificationTokenGenerator;
import io.github.aayush10016.trail.identity.security.SecurePasswordResetTokenGenerator;
import io.github.aayush10016.trail.identity.security.SecureRefreshTokenGenerator;
import io.github.aayush10016.trail.identity.security.Sha256EmailActionTokenHashingPolicy;
import io.github.aayush10016.trail.identity.security.Sha256RefreshTokenHashingPolicy;
import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Identity capability wiring.
 */
@Configuration
@EnableConfigurationProperties({IdentityLifecycleProperties.class, IdentityEmailDeliveryProperties.class})
public class IdentityConfiguration {

    /**
     * Creates the current password hashing and verification policy.
     */
    @Bean
    public Argon2idPasswordPolicy passwordPolicy() {
        return new Argon2idPasswordPolicy();
    }

    /**
     * Creates the secure random source for identity credentials.
     */
    @Bean
    public SecureRandom identitySecureRandom() {
        return new SecureRandom();
    }

    /**
     * Creates the refresh-token generator.
     */
    @Bean
    public RefreshTokenGenerator refreshTokenGenerator(SecureRandom identitySecureRandom) {
        return new SecureRefreshTokenGenerator(identitySecureRandom);
    }

    /**
     * Creates the refresh-token hashing policy.
     */
    @Bean
    public RefreshTokenHashingPolicy refreshTokenHashingPolicy() {
        return new Sha256RefreshTokenHashingPolicy();
    }

    /**
     * Creates the email action token hashing policy.
     */
    @Bean
    public EmailActionTokenHashingPolicy emailActionTokenHashingPolicy() {
        return new Sha256EmailActionTokenHashingPolicy();
    }

    /**
     * Creates the email verification token generator.
     */
    @Bean
    public EmailVerificationTokenGenerator emailVerificationTokenGenerator(SecureRandom identitySecureRandom) {
        return new SecureEmailVerificationTokenGenerator(identitySecureRandom);
    }

    /**
     * Creates the password-reset token generator.
     */
    @Bean
    public PasswordResetTokenGenerator passwordResetTokenGenerator(SecureRandom identitySecureRandom) {
        return new SecurePasswordResetTokenGenerator(identitySecureRandom);
    }

    /**
     * Creates identity abuse-control rate limiting.
     */
    @Bean
    public IdentityRateLimiter identityRateLimiter() {
        return new InMemoryIdentityRateLimiter();
    }

    /**
     * Creates the local email delivery adapter.
     */
    @Bean
    public LocalEmailDeliveryAdapter localEmailDeliveryAdapter() {
        return new LocalEmailDeliveryAdapter();
    }

    /**
     * Selects the configured identity email delivery adapter.
     */
    @Bean
    @Primary
    public EmailDeliveryPort emailDeliveryPort(
            IdentityEmailDeliveryProperties properties,
            LocalEmailDeliveryAdapter localEmailDeliveryAdapter,
            ObjectMapper objectMapper) {
        return switch (properties.provider()) {
            case LOCAL -> localEmailDeliveryAdapter;
            case BREVO -> new BrevoEmailDeliveryAdapter(
                    HttpClient.newBuilder()
                            .connectTimeout(properties.timeoutOrDefault())
                            .build(),
                    objectMapper,
                    properties.brevo().endpoint(),
                    properties.brevo().apiKey(),
                    properties.fromAddress(),
                    properties.fromName(),
                    properties.timeoutOrDefault());
        };
    }

    /**
     * Creates identity email templates.
     */
    @Bean
    public IdentityEmailTemplateFactory identityEmailTemplateFactory(IdentityLifecycleProperties properties) {
        return new IdentityEmailTemplateFactory(
                properties.verificationDestinationOrDefault(),
                properties.passwordResetDestinationOrDefault(),
                properties.emailVerificationTokenTimeToLiveOrDefault(),
                properties.passwordResetTokenTimeToLiveOrDefault());
    }

    /**
     * Creates the registration use case.
     */
    @Bean
    public RegisterUserService registerUserService(
            io.github.aayush10016.trail.identity.domain.port.UserRegistrationRepository userRegistrationRepository,
            Argon2idPasswordPolicy passwordHashingPolicy,
            IdentityRateLimiter identityRateLimiter,
            Clock clock) {
        return new RegisterUserService(
                userRegistrationRepository, passwordHashingPolicy, identityRateLimiter, clock, UUID::randomUUID);
    }

    /**
     * Creates the email-verification use case.
     */
    @Bean
    public EmailVerificationService emailVerificationService(
            EmailVerificationTokenRepository tokenRepository,
            EmailVerificationTokenGenerator tokenGenerator,
            EmailActionTokenHashingPolicy tokenHashingPolicy,
            EmailDeliveryPort emailDeliveryPort,
            IdentityEmailTemplateFactory emailTemplateFactory,
            IdentityRateLimiter identityRateLimiter,
            Clock clock,
            IdentityLifecycleProperties properties) {
        return new EmailVerificationService(
                tokenRepository,
                tokenGenerator,
                tokenHashingPolicy,
                emailDeliveryPort,
                emailTemplateFactory,
                identityRateLimiter,
                clock,
                UUID::randomUUID,
                properties.emailVerificationTokenTimeToLiveOrDefault());
    }

    /**
     * Creates the refresh-session use case.
     */
    @Bean
    public RefreshSessionService refreshSessionService(
            RefreshSessionRepository refreshSessionRepository,
            RefreshTokenGenerator refreshTokenGenerator,
            RefreshTokenHashingPolicy refreshTokenHashingPolicy,
            AccessTokenIssuer accessTokenIssuer,
            Clock clock,
            IdentityLifecycleProperties properties) {
        return new RefreshSessionService(
                refreshSessionRepository,
                refreshTokenGenerator,
                refreshTokenHashingPolicy,
                accessTokenIssuer,
                clock,
                UUID::randomUUID,
                UUID::randomUUID,
                properties.refreshTokenTimeToLiveOrDefault());
    }

    /**
     * Creates the login use case.
     */
    @Bean
    public LoginUserService loginUserService(
            LoginUserRepository loginUserRepository,
            Argon2idPasswordPolicy passwordVerificationPolicy,
            AccessTokenIssuer accessTokenIssuer,
            RefreshSessionService refreshSessionService,
            IdentityRateLimiter identityRateLimiter,
            Clock clock) {
        return new LoginUserService(
                loginUserRepository,
                passwordVerificationPolicy,
                accessTokenIssuer,
                refreshSessionService,
                identityRateLimiter,
                clock);
    }

    /**
     * Creates the password-reset request use case.
     */
    @Bean
    public PasswordResetRequestService passwordResetRequestService(
            PasswordResetTokenRepository tokenRepository,
            PasswordResetTokenGenerator tokenGenerator,
            EmailActionTokenHashingPolicy tokenHashingPolicy,
            EmailDeliveryPort emailDeliveryPort,
            IdentityEmailTemplateFactory emailTemplateFactory,
            IdentityRateLimiter identityRateLimiter,
            Clock clock,
            IdentityLifecycleProperties properties) {
        return new PasswordResetRequestService(
                tokenRepository,
                tokenGenerator,
                tokenHashingPolicy,
                emailDeliveryPort,
                emailTemplateFactory,
                identityRateLimiter,
                clock,
                UUID::randomUUID,
                properties.passwordResetTokenTimeToLiveOrDefault());
    }

    /**
     * Creates the password reset use case.
     */
    @Bean
    public PasswordResetService passwordResetService(
            PasswordResetTokenRepository tokenRepository,
            RefreshSessionRepository refreshSessionRepository,
            EmailActionTokenHashingPolicy tokenHashingPolicy,
            Argon2idPasswordPolicy passwordHashingPolicy,
            IdentityRateLimiter identityRateLimiter,
            Clock clock) {
        return new PasswordResetService(
                tokenRepository,
                refreshSessionRepository,
                tokenHashingPolicy,
                passwordHashingPolicy,
                identityRateLimiter,
                clock);
    }

    /**
     * Creates the account deletion service.
     */
    @Bean
    public AccountDeletionService accountDeletionService(
            AccountDeletionRepository accountDeletionRepository,
            Argon2idPasswordPolicy passwordVerificationPolicy,
            Clock clock) {
        return new AccountDeletionService(accountDeletionRepository, passwordVerificationPolicy, clock);
    }
}
