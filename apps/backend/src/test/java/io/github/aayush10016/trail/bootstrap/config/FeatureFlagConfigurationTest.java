package io.github.aayush10016.trail.bootstrap.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aayush10016.trail.shared.featureflag.FeatureFlag;
import io.github.aayush10016.trail.shared.featureflag.FeatureFlagService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        properties = {
            "trail.features.ai-suggestions=true",
            "trail.features.ghost-replay=false",
            "trail.features.semantic-search=true",
            "trail.features.experimental-features=false"
        })
class FeatureFlagConfigurationTest {

    @Autowired
    private FeatureFlagService featureFlagService;

    @Test
    void shouldBindTypedConfigurationPropertiesIntoFeatureFlagService() {
        assertThat(featureFlagService.isEnabled(FeatureFlag.AI_SUGGESTIONS)).isTrue();
        assertThat(featureFlagService.isEnabled(FeatureFlag.GHOST_REPLAY)).isFalse();
        assertThat(featureFlagService.isEnabled(FeatureFlag.SEMANTIC_SEARCH)).isTrue();
        assertThat(featureFlagService.isEnabled(FeatureFlag.EXPERIMENTAL_FEATURES)).isFalse();
    }
}
