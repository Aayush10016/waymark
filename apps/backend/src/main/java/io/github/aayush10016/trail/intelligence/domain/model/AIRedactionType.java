package io.github.aayush10016.trail.intelligence.domain.model;

/**
 * Stable redaction categories for provider-safe AI input.
 */
public enum AIRedactionType {
    SECRET,
    PII,
    PRIVATE_PATH,
    PRIVATE_HOST,
    UUID_LIKE_TOKEN,
    ADVERSARIAL_INSTRUCTION,
    LOG_CANARY
}
