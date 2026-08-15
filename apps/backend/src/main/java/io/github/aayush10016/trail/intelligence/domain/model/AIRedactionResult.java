package io.github.aayush10016.trail.intelligence.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Redaction result containing either provider-safe input or refusal metadata.
 */
public record AIRedactionResult(
        AIRedactionStatus status,
        String policyVersion,
        String safeInput,
        List<AIRedaction> redactions,
        AIRefusalReason refusalReason) {

    /**
     * Creates a validated redaction result.
     */
    public AIRedactionResult {
        Objects.requireNonNull(status, "status");
        if (policyVersion == null || policyVersion.isBlank()) {
            throw new IllegalArgumentException("policyVersion must not be blank");
        }
        policyVersion = policyVersion.strip();
        redactions = List.copyOf(Objects.requireNonNull(redactions, "redactions"));
        if (status == AIRedactionStatus.ALLOWED) {
            if (safeInput == null || safeInput.isBlank()) {
                throw new IllegalArgumentException("safeInput is required when allowed");
            }
            if (refusalReason != null) {
                throw new IllegalArgumentException("refusalReason must be null when allowed");
            }
            safeInput = safeInput.strip();
        } else {
            if (safeInput != null) {
                throw new IllegalArgumentException("safeInput must be null when refused");
            }
            Objects.requireNonNull(refusalReason, "refusalReason");
        }
    }

    /**
     * Creates an allowed result.
     */
    public static AIRedactionResult allowed(String policyVersion, String safeInput, List<AIRedaction> redactions) {
        return new AIRedactionResult(AIRedactionStatus.ALLOWED, policyVersion, safeInput, redactions, null);
    }

    /**
     * Creates a refused result.
     */
    public static AIRedactionResult refused(
            String policyVersion,
            List<AIRedaction> redactions,
            AIRefusalReason refusalReason) {
        return new AIRedactionResult(AIRedactionStatus.REFUSED, policyVersion, null, redactions, refusalReason);
    }

    /**
     * Whether provider input is safe enough to send.
     */
    public boolean allowed() {
        return status == AIRedactionStatus.ALLOWED;
    }

    /**
     * Optional refusal reason.
     */
    public Optional<AIRefusalReason> refusalReasonOptional() {
        return Optional.ofNullable(refusalReason);
    }
}
