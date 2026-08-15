package io.github.aayush10016.trail.identity.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(OutputCaptureExtension.class)
class IdentityLifecycleApiExceptionHandlerTest {

    private final IdentityLifecycleApiExceptionHandler handler = new IdentityLifecycleApiExceptionHandler();

    @Test
    void logsUnavailableOperationCauseWhileKeepingClientResponseSafe(CapturedOutput output) {
        var response = handler.handleUnavailableIdentityOperation(
                new IllegalStateException("JWT access-token signing key is not configured"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("Identity operation is unavailable.");
        assertThat(response.getBody().getProperties()).containsEntry("code", "IDENTITY_OPERATION_UNAVAILABLE");
        assertThat(response.getBody().getDetail()).doesNotContain("JWT access-token signing key");
        assertThat(output).contains("JWT access-token signing key is not configured");
    }
}
