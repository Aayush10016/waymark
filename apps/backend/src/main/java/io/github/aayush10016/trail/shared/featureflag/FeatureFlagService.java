package io.github.aayush10016.trail.shared.featureflag;

/**
 * Evaluates approved server-side feature flags.
 */
public interface FeatureFlagService {

    boolean isEnabled(FeatureFlag featureFlag);
}
