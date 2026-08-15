package io.github.aayush10016.trail.bootstrap.config;

import io.github.aayush10016.trail.shared.featureflag.FeatureFlagService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires global configuration-backed feature flags.
 */
@Configuration
@EnableConfigurationProperties(FeatureFlagProperties.class)
public class FeatureFlagConfiguration {

    @Bean
    public FeatureFlagService featureFlagService(FeatureFlagProperties properties) {
        return new ConfigurationBackedFeatureFlagService(properties);
    }
}
