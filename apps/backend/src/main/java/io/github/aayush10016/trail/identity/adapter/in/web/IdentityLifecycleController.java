package io.github.aayush10016.trail.identity.adapter.in.web;

import io.github.aayush10016.trail.identity.application.command.ConsumeEmailVerificationCommand;
import io.github.aayush10016.trail.identity.application.command.LoginCommand;
import io.github.aayush10016.trail.identity.application.command.RegisterUserCommand;
import io.github.aayush10016.trail.identity.application.command.RequestEmailVerificationCommand;
import io.github.aayush10016.trail.identity.application.command.RequestPasswordResetCommand;
import io.github.aayush10016.trail.identity.application.command.ResetPasswordCommand;
import io.github.aayush10016.trail.identity.application.command.RotateRefreshSessionCommand;
import io.github.aayush10016.trail.identity.application.command.RevokeCurrentSessionCommand;
import io.github.aayush10016.trail.identity.application.dto.LoginStatus;
import io.github.aayush10016.trail.identity.application.dto.RefreshSessionStatus;
import io.github.aayush10016.trail.identity.application.service.EmailVerificationService;
import io.github.aayush10016.trail.identity.application.service.LoginUserService;
import io.github.aayush10016.trail.identity.application.service.PasswordResetRequestService;
import io.github.aayush10016.trail.identity.application.service.PasswordResetService;
import io.github.aayush10016.trail.identity.application.service.RefreshSessionService;
import io.github.aayush10016.trail.identity.application.service.RegisterUserService;
import io.github.aayush10016.trail.identity.domain.model.IdentityRequestContext;
import io.github.aayush10016.trail.identity.domain.model.IssuedAccessToken;
import io.github.aayush10016.trail.identity.domain.model.IssuedRefreshToken;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for Trail's email/password identity lifecycle.
 */
@RestController
@RequestMapping("/api/v1")
public class IdentityLifecycleController {

    private final RegisterUserService registerUserService;
    private final EmailVerificationService emailVerificationService;
    private final LoginUserService loginUserService;
    private final RefreshSessionService refreshSessionService;
    private final PasswordResetRequestService passwordResetRequestService;
    private final PasswordResetService passwordResetService;

    /**
     * Creates the controller.
     */
    public IdentityLifecycleController(
            RegisterUserService registerUserService,
            EmailVerificationService emailVerificationService,
            LoginUserService loginUserService,
            RefreshSessionService refreshSessionService,
            PasswordResetRequestService passwordResetRequestService,
            PasswordResetService passwordResetService) {
        this.registerUserService = Objects.requireNonNull(registerUserService, "registerUserService");
        this.emailVerificationService = Objects.requireNonNull(emailVerificationService, "emailVerificationService");
        this.loginUserService = Objects.requireNonNull(loginUserService, "loginUserService");
        this.refreshSessionService = Objects.requireNonNull(refreshSessionService, "refreshSessionService");
        this.passwordResetRequestService =
                Objects.requireNonNull(passwordResetRequestService, "passwordResetRequestService");
        this.passwordResetService = Objects.requireNonNull(passwordResetService, "passwordResetService");
    }

    /**
     * Registers an unverified email/password account and sends verification.
     */
    @PostMapping("/registrations")
    public ResponseEntity<IdentityMutationResponse> register(
            @RequestBody RegistrationRequest request, HttpServletRequest httpRequest) {
        requireBody(request);
        registerUserService.register(new RegisterUserCommand(
                request.email(), request.password(), requestContext(httpRequest)));
        emailVerificationService.requestVerification(
                new RequestEmailVerificationCommand(request.email(), requestContext(httpRequest)));
        return accepted();
    }

    /**
     * Requests or resends an email verification token.
     */
    @PostMapping("/email-verification-requests")
    public ResponseEntity<IdentityMutationResponse> requestEmailVerification(
            @RequestBody EmailRequest request, HttpServletRequest httpRequest) {
        requireBody(request);
        emailVerificationService.requestVerification(
                new RequestEmailVerificationCommand(request.email(), requestContext(httpRequest)));
        return accepted();
    }

    /**
     * Consumes an email verification token.
     */
    @PostMapping("/email-verifications")
    public ResponseEntity<IdentityMutationResponse> verifyEmail(
            @RequestBody TokenRequest request, HttpServletRequest httpRequest) {
        requireBody(request);
        emailVerificationService.consumeVerification(
                new ConsumeEmailVerificationCommand(request.token(), requestContext(httpRequest)));
        return accepted();
    }

    /**
     * Authenticates email/password credentials and creates an auth session.
     */
    @PostMapping("/auth-sessions")
    public ResponseEntity<IssuedAuthTokensResponse> createAuthSession(
            @RequestBody RegistrationRequest request, HttpServletRequest httpRequest) {
        requireBody(request);
        var result = loginUserService.login(
                new LoginCommand(request.email(), request.password(), requestContext(httpRequest)));
        if (result.status() != LoginStatus.AUTHENTICATED) {
            throw IdentityApiException.unauthorized();
        }
        return ResponseEntity.ok(IssuedAuthTokensResponse.from(result.accessToken(), result.refreshToken()));
    }

    /**
     * Rotates the presented refresh credential and issues new credentials.
     */
    @PostMapping("/auth-sessions/refresh")
    public ResponseEntity<IssuedAuthTokensResponse> refreshAuthSession(@RequestBody RefreshSessionRequest request) {
        requireBody(request);
        var result = refreshSessionService.rotate(new RotateRefreshSessionCommand(request.refreshToken()));
        if (result.status() != RefreshSessionStatus.ROTATED) {
            throw IdentityApiException.unauthorized();
        }
        return ResponseEntity.ok(IssuedAuthTokensResponse.from(result.accessToken(), result.refreshToken()));
    }

    /**
     * Revokes the refresh-session family containing the presented credential.
     */
    @DeleteMapping("/auth-sessions/current")
    public ResponseEntity<Void> revokeCurrentSession(@RequestBody RefreshSessionRequest request) {
        requireBody(request);
        refreshSessionService.revokeCurrentSession(new RevokeCurrentSessionCommand(request.refreshToken()));
        return ResponseEntity.noContent().build();
    }

    /**
     * Requests a password reset token.
     */
    @PostMapping("/password-reset-requests")
    public ResponseEntity<IdentityMutationResponse> requestPasswordReset(
            @RequestBody EmailRequest request, HttpServletRequest httpRequest) {
        requireBody(request);
        passwordResetRequestService.requestReset(
                new RequestPasswordResetCommand(request.email(), requestContext(httpRequest)));
        return accepted();
    }

    /**
     * Consumes a password reset token and replaces the account password.
     */
    @PostMapping("/password-resets")
    public ResponseEntity<IdentityMutationResponse> resetPassword(
            @RequestBody PasswordResetRequest request, HttpServletRequest httpRequest) {
        requireBody(request);
        passwordResetService.resetPassword(
                new ResetPasswordCommand(request.token(), request.newPassword(), requestContext(httpRequest)));
        return accepted();
    }

    private static IdentityRequestContext requestContext(HttpServletRequest request) {
        var remoteAddress = request.getRemoteAddr();
        return remoteAddress == null || remoteAddress.isBlank()
                ? IdentityRequestContext.unspecified()
                : new IdentityRequestContext(remoteAddress);
    }

    private static ResponseEntity<IdentityMutationResponse> accepted() {
        return ResponseEntity.accepted().body(new IdentityMutationResponse("ACCEPTED"));
    }

    private static void requireBody(Object request) {
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
    }

    /**
     * Email/password request.
     */
    public record RegistrationRequest(String email, String password) {

        @Override
        public String toString() {
            return "RegistrationRequest[emailPresent=" + (email != null) + ", passwordPresent=" + (password != null) + "]";
        }
    }

    /**
     * Email-only request.
     */
    public record EmailRequest(String email) {

        @Override
        public String toString() {
            return "EmailRequest[emailPresent=" + (email != null) + "]";
        }
    }

    /**
     * Token-only request.
     */
    public record TokenRequest(String token) {

        @Override
        public String toString() {
            return "TokenRequest[tokenPresent=" + (token != null) + "]";
        }
    }

    /**
     * Refresh credential request.
     */
    public record RefreshSessionRequest(String refreshToken) {

        @Override
        public String toString() {
            return "RefreshSessionRequest[refreshTokenPresent=" + (refreshToken != null) + "]";
        }
    }

    /**
     * Password-reset consume request.
     */
    public record PasswordResetRequest(String token, String newPassword) {

        @Override
        public String toString() {
            return "PasswordResetRequest[tokenPresent="
                    + (token != null)
                    + ", newPasswordPresent="
                    + (newPassword != null)
                    + "]";
        }
    }

    /**
     * Generic identity mutation response.
     */
    public record IdentityMutationResponse(String status) {}

    /**
     * Credential issuance response consumed by the extension.
     */
    public record IssuedAuthTokensResponse(
            String accessToken,
            Instant accessTokenExpiresAt,
            String refreshToken,
            Instant refreshTokenExpiresAt) {

        static IssuedAuthTokensResponse from(IssuedAccessToken accessToken, IssuedRefreshToken refreshToken) {
            return new IssuedAuthTokensResponse(
                    accessToken.token(), accessToken.expiresAt(), refreshToken.token(), refreshToken.expiresAt());
        }

        @Override
        public String toString() {
            return "IssuedAuthTokensResponse[accessTokenPresent="
                    + (accessToken != null)
                    + ", accessTokenExpiresAt="
                    + accessTokenExpiresAt
                    + ", refreshTokenPresent="
                    + (refreshToken != null)
                    + ", refreshTokenExpiresAt="
                    + refreshTokenExpiresAt
                    + "]";
        }
    }
}
