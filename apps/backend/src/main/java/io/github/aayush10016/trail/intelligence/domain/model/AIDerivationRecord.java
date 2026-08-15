package io.github.aayush10016.trail.intelligence.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Structured, content-bounded provenance for one AI derivation attempt.
 */
public record AIDerivationRecord(
        UUID id,
        UUID userId,
        UUID problemSessionId,
        AIDerivationType derivationType,
        String provider,
        String model,
        PromptIdentity promptIdentity,
        String outputSchemaHash,
        String policyVersion,
        String inputFingerprint,
        AIDerivationStatus status,
        String safeFailureCode,
        String generatedSummary,
        List<String> technologies,
        List<String> frameworks,
        List<String> errorPhrases,
        String problemType,
        AIUsageMetadata usage,
        AICostMetadata cost,
        Instant completedAt,
        Instant createdAt) {

    private static final int MAX_PROVIDER_LENGTH = 64;
    private static final int MAX_MODEL_LENGTH = 128;
    private static final int MAX_FAILURE_CODE_LENGTH = 128;
    private static final int MAX_SUMMARY_LENGTH = 4000;
    private static final int MAX_ARRAY_SIZE = 32;
    private static final int MAX_ARRAY_VALUE_LENGTH = 240;
    private static final int MAX_PROBLEM_TYPE_LENGTH = 64;

    /**
     * Creates validated derivation provenance.
     */
    public AIDerivationRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(problemSessionId, "problemSessionId");
        Objects.requireNonNull(derivationType, "derivationType");
        provider = requireBounded(provider, "provider", MAX_PROVIDER_LENGTH);
        model = requireBounded(model, "model", MAX_MODEL_LENGTH);
        Objects.requireNonNull(promptIdentity, "promptIdentity");
        outputSchemaHash = requireSha256(outputSchemaHash, "outputSchemaHash");
        policyVersion = requireBounded(policyVersion, "policyVersion", MAX_MODEL_LENGTH);
        inputFingerprint = requireSha256(inputFingerprint, "inputFingerprint");
        Objects.requireNonNull(status, "status");
        safeFailureCode = optionalBounded(safeFailureCode, "safeFailureCode", MAX_FAILURE_CODE_LENGTH);
        generatedSummary = optionalBounded(generatedSummary, "generatedSummary", MAX_SUMMARY_LENGTH);
        technologies = boundedList(technologies, "technologies");
        frameworks = boundedList(frameworks, "frameworks");
        errorPhrases = boundedList(errorPhrases, "errorPhrases");
        problemType = optionalBounded(problemType, "problemType", MAX_PROBLEM_TYPE_LENGTH);
        usage = Objects.requireNonNull(usage, "usage");
        cost = Objects.requireNonNull(cost, "cost");
        Objects.requireNonNull(createdAt, "createdAt");
        if (status == AIDerivationStatus.SUCCEEDED && safeFailureCode != null) {
            throw new IllegalArgumentException("successful derivations must not have a failure code");
        }
        if (status != AIDerivationStatus.SUCCEEDED && safeFailureCode == null) {
            throw new IllegalArgumentException("failed derivations require a safe failure code");
        }
        if (completedAt != null
                && status != AIDerivationStatus.SUCCEEDED
                && status != AIDerivationStatus.FAILED_TERMINAL
                && status != AIDerivationStatus.REFUSED
                && status != AIDerivationStatus.INVALID_OUTPUT) {
            throw new IllegalArgumentException("retryable derivations must not be completed");
        }
    }

    private static List<String> boundedList(List<String> values, String fieldName) {
        var copied = List.copyOf(Objects.requireNonNull(values, fieldName));
        if (copied.size() > MAX_ARRAY_SIZE) {
            throw new IllegalArgumentException(fieldName + " must be bounded");
        }
        copied.forEach(value -> requireBounded(value, fieldName + " value", MAX_ARRAY_VALUE_LENGTH));
        return copied;
    }

    private static String requireSha256(String value, String fieldName) {
        var sanitized = requireBounded(value, fieldName, 64);
        if (!sanitized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(fieldName + " must be a SHA-256 hex digest");
        }
        return sanitized;
    }

    private static String optionalBounded(String value, String fieldName, int maxLength) {
        if (value == null) {
            return null;
        }
        return requireBounded(value, fieldName, maxLength);
    }

    private static String requireBounded(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        var sanitized = value.strip();
        if (sanitized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must be bounded");
        }
        return sanitized;
    }
}
