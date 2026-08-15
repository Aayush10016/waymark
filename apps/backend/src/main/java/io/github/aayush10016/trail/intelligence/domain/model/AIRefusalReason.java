package io.github.aayush10016.trail.intelligence.domain.model;

/**
 * Safe refusal reasons for AI calls.
 */
public enum AIRefusalReason {
    /**
     * Not enough non-sensitive signal remains after redaction.
     */
    INSUFFICIENT_SAFE_INPUT
}
