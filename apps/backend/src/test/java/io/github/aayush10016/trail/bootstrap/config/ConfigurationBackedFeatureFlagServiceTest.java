package io.github.aayush10016.trail.bootstrap.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import io.github.aayush10016.trail.shared.featureflag.FeatureFlag;
import org.junit.jupiter.api.Test;

class ConfigurationBackedFeatureFlagServiceTest {

    @Test
    void shouldUseSafeDefaultsWhenNoFlagsAreConfigured() {
        var service = new ConfigurationBackedFeatureFlagService(new FeatureFlagProperties(null, null, null, null));

        assertThat(service.isEnabled(FeatureFlag.AI_SUGGESTIONS)).isFalse();
        assertThat(service.isEnabled(FeatureFlag.GHOST_REPLAY)).isFalse();
        assertThat(service.isEnabled(FeatureFlag.SEMANTIC_SEARCH)).isFalse();
        assertThat(service.isEnabled(FeatureFlag.EXPERIMENTAL_FEATURES)).isFalse();
    }

    @Test
    void shouldUseExplicitConfigurationValues() {
        var service = new ConfigurationBackedFeatureFlagService(new FeatureFlagProperties(true, false, true, false));

        assertThat(service.isEnabled(FeatureFlag.AI_SUGGESTIONS)).isTrue();
        assertThat(service.isEnabled(FeatureFlag.GHOST_REPLAY)).isFalse();
        assertThat(service.isEnabled(FeatureFlag.SEMANTIC_SEARCH)).isTrue();
        assertThat(service.isEnabled(FeatureFlag.EXPERIMENTAL_FEATURES)).isFalse();
    }

    @Test
    void shouldRejectNullFlagEvaluation() {
        var service = new ConfigurationBackedFeatureFlagService(new FeatureFlagProperties(null, null, null, null));

        assertThatNullPointerException().isThrownBy(() -> service.isEnabled(null));
    }
}
