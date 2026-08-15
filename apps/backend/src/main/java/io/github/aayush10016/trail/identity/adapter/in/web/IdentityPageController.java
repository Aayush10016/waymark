package io.github.aayush10016.trail.identity.adapter.in.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the minimal public identity pages used by verification and recovery
 * emails.
 */
@Controller
public class IdentityPageController {

    /**
     * Serves the email verification page.
     *
     * @return internal static-resource forward
     */
    @GetMapping("/verify-email")
    public String verifyEmailPage() {
        return "forward:/verify-email/index.html";
    }

    /**
     * Serves the password reset page.
     *
     * @return internal static-resource forward
     */
    @GetMapping("/reset-password")
    public String resetPasswordPage() {
        return "forward:/reset-password/index.html";
    }
}
