package io.github.aayush10016.trail.shared.featureflag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class FeatureFlagTest {

    @Test
    void shouldDefineOnlyApprovedGlobalFlags() {
        assertThat(FeatureFlag.values())
                .containsExactly(
                        FeatureFlag.AI_SUGGESTIONS,
                        FeatureFlag.GHOST_REPLAY,
                        FeatureFlag.SEMANTIC_SEARCH,
                        FeatureFlag.EXPERIMENTAL_FEATURES);
    }

    @Test
    void shouldKeepAllInitialFlagsDisabledByDefault() {
        assertThat(FeatureFlag.values()).extracting(FeatureFlag::defaultEnabled).containsOnly(false);
    }

    @Test
    void shouldRequireOwnerPurposeCreationDateAndRemovalConditionForEveryFlag() {
        assertThat(FeatureFlag.values())
                .allSatisfy(flag -> {
                    assertThat(flag.key()).isNotBlank();
                    assertThat(flag.owner()).isNotBlank();
                    assertThat(flag.purpose()).isNotBlank();
                    assertThat(flag.createdOn()).isNotNull();
                    assertThat(flag.removalCondition()).isNotBlank();
                });
    }

    @Test
    void shouldNotDefineSecurityPrivacyOrIdentityControlsAsFlags() {
        var flagNamesAndKeys = Arrays.stream(FeatureFlag.values())
                .flatMap(flag -> java.util.stream.Stream.of(flag.name(), flag.key(), flag.purpose()))
                .map(String::toLowerCase)
                .toList();

        assertThat(flagNamesAndKeys)
                .noneMatch(value -> value.contains("auth"))
                .noneMatch(value -> value.contains("authorization"))
                .noneMatch(value -> value.contains("sanitation"))
                .noneMatch(value -> value.contains("sanitization"))
                .noneMatch(value -> value.contains("consent"))
                .noneMatch(value -> value.contains("deletion"));
    }
}
