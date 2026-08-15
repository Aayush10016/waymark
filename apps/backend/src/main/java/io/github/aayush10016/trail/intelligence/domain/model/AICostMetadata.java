package io.github.aayush10016.trail.intelligence.domain.model;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * Provider-neutral cost metadata for an AI call.
 */
public record AICostMetadata(BigDecimal estimatedCost, Currency currency) {

    /**
     * Creates validated cost metadata.
     */
    public AICostMetadata {
        Objects.requireNonNull(estimatedCost, "estimatedCost");
        Objects.requireNonNull(currency, "currency");
        if (estimatedCost.signum() < 0) {
            throw new IllegalArgumentException("estimatedCost must not be negative");
        }
    }

    /**
     * Zero USD cost metadata for local/mock calls.
     */
    public static AICostMetadata zeroUsd() {
        return new AICostMetadata(BigDecimal.ZERO, Currency.getInstance("USD"));
    }
}
