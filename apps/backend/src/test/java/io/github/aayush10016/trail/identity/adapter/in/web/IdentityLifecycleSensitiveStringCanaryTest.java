package io.github.aayush10016.trail.identity.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aayush10016.trail.identity.application.command.ConsumeEmailVerificationCommand;
import io.github.aayush10016.trail.identity.application.command.LoginCommand;
import io.github.aayush10016.trail.identity.application.command.RegisterUserCommand;
import io.github.aayush10016.trail.identity.application.command.RequestEmailVerificationCommand;
import io.github.aayush10016.trail.identity.application.command.RequestPasswordResetCommand;
import io.github.aayush10016.trail.identity.application.command.ResetPasswordCommand;
import io.github.aayush10016.trail.identity.application.command.RotateRefreshSessionCommand;
import io.github.aayush10016.trail.identity.application.command.RevokeCurrentSessionCommand;
import io.github.aayush10016.trail.identity.adapter.in.web.IdentityLifecycleController.IssuedAuthTokensResponse;
import io.github.aayush10016.trail.identity.domain.model.IssuedAccessToken;
import io.github.aayush10016.trail.identity.domain.model.IssuedRefreshToken;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class IdentityLifecycleSensitiveStringCanaryTest {

    @Test
    void requestAndCommandStringRepresentationsDoNotExposeCredentialMaterial() {
        var representations = List.of(
                new IdentityLifecycleController.RegistrationRequest("developer@example.com", "raw-password").toString(),
                new IdentityLifecycleController.EmailRequest("developer@example.com").toString(),
                new IdentityLifecycleController.TokenRequest("verification-token").toString(),
                new IdentityLifecycleController.RefreshSessionRequest("refresh-token").toString(),
                new IdentityLifecycleController.PasswordResetRequest("reset-token", "new-password").toString(),
                new RegisterUserCommand("developer@example.com", "raw-password").toString(),
                new LoginCommand("developer@example.com", "raw-password").toString(),
                new RequestEmailVerificationCommand("developer@example.com").toString(),
                new ConsumeEmailVerificationCommand("verification-token").toString(),
                new RequestPasswordResetCommand("developer@example.com").toString(),
                new ResetPasswordCommand("reset-token", "new-password").toString(),
                new RotateRefreshSessionCommand("refresh-token").toString(),
                new RevokeCurrentSessionCommand("refresh-token").toString(),
                IssuedAuthTokensResponse.from(
                                IssuedAccessToken.bearer("access-token", Instant.parse("2026-07-28T12:00:00Z")),
                                IssuedRefreshToken.bearer("refresh-token", Instant.parse("2026-08-28T12:00:00Z")))
                        .toString());

        assertThat(String.join("\n", representations))
                .doesNotContain("developer@example.com")
                .doesNotContain("raw-password")
                .doesNotContain("verification-token")
                .doesNotContain("reset-token")
                .doesNotContain("new-password")
                .doesNotContain("access-token")
                .doesNotContain("refresh-token");
    }
}
