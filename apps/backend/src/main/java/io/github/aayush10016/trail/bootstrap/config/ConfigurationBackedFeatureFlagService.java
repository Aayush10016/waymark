package io.github.aayush10016.trail.bootstrap.config;

import io.github.aayush10016.trail.shared.featureflag.FeatureFlag;
import io.github.aayush10016.trail.shared.featureflag.FeatureFlagService;
import java.util.Objects;

/**
 * Feature flag service backed by validated Spring configuration values.
 */
public final class ConfigurationBackedFeatureFlagService implements FeatureFlagService {

    private final FeatureFlagProperties properties;

    public ConfigurationBackedFeatureFlagService(FeatureFlagProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public boolean isEnabled(FeatureFlag featureFlag) {
        return properties.isEnabled(Objects.requireNonNull(featureFlag, "featureFlag must not be null"));
    }
}
