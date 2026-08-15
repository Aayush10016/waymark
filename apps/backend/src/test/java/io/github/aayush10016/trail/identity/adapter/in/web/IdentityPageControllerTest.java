package io.github.aayush10016.trail.identity.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = IdentityPageController.class)
@ContextConfiguration(classes = IdentityPageController.class)
class IdentityPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void servesVerificationPageWithoutRenderingTokenFromQuery() throws Exception {
        mockMvc.perform(get("/verify-email").queryParam("token", "secret-token-canary"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/verify-email/index.html"));

        assertThat(staticAsset("/static/verify-email/index.html"))
                .contains("Verifying your email")
                .doesNotContain("secret-token-canary");
    }

    @Test
    void servesPasswordResetPageWithoutRenderingTokenFromQuery() throws Exception {
        mockMvc.perform(get("/reset-password").queryParam("token", "secret-reset-canary"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/reset-password/index.html"));

        assertThat(staticAsset("/static/reset-password/index.html"))
                .contains("Reset your password")
                .doesNotContain("secret-reset-canary");
    }

    @Test
    void identityPagesDoNotPersistTokensOrUseConsoleLogging() throws IOException {
        assertSafeStaticAsset("/static/identity/verify-email.js");
        assertSafeStaticAsset("/static/identity/reset-password.js");
    }

    private static void assertSafeStaticAsset(String resourcePath) throws IOException {
        var content = staticAsset(resourcePath);
        assertThat(content)
                .contains("history.replaceState")
                .doesNotContain("localStorage")
                .doesNotContain("sessionStorage")
                .doesNotContain("console.")
                .doesNotContain("chrome.storage");
    }

    private static String staticAsset(String resourcePath) throws IOException {
        var stream = IdentityPageControllerTest.class.getResourceAsStream(resourcePath);
        assertThat(stream).isNotNull();
        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
}
