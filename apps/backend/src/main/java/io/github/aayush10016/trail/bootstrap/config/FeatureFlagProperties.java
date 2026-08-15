package io.github.aayush10016.trail.bootstrap.config;

import io.github.aayush10016.trail.shared.featureflag.FeatureFlag;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration values for approved global backend feature flags.
 */
@ConfigurationProperties(prefix = "trail.features")
public record FeatureFlagProperties(
        Boolean aiSuggestions, Boolean ghostReplay, Boolean semanticSearch, Boolean experimentalFeatures) {

    public boolean isEnabled(FeatureFlag featureFlag) {
        return switch (featureFlag) {
            case AI_SUGGESTIONS -> configuredValue(aiSuggestions, featureFlag);
            case GHOST_REPLAY -> configuredValue(ghostReplay, featureFlag);
            case SEMANTIC_SEARCH -> configuredValue(semanticSearch, featureFlag);
            case EXPERIMENTAL_FEATURES -> configuredValue(experimentalFeatures, featureFlag);
        };
    }

    private boolean configuredValue(Boolean configuredValue, FeatureFlag featureFlag) {
        return configuredValue == null ? featureFlag.defaultEnabled() : configuredValue;
    }
}
