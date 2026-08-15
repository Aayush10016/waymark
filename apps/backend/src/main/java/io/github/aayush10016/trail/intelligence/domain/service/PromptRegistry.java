package io.github.aayush10016.trail.intelligence.domain.service;

import io.github.aayush10016.trail.intelligence.domain.model.PromptDefinition;
import io.github.aayush10016.trail.intelligence.domain.model.PromptIdentity;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Immutable registry of approved prompt definitions.
 */
public class PromptRegistry {

    private final Map<PromptLookupKey, PromptDefinition> definitions;

    /**
     * Creates an immutable prompt registry.
     */
    public PromptRegistry(Collection<PromptDefinition> definitions) {
        Objects.requireNonNull(definitions, "definitions");
        this.definitions = Map.copyOf(definitions.stream()
                .collect(Collectors.toMap(
                        definition -> PromptLookupKey.from(definition.identity()),
                        definition -> definition,
                        (left, right) -> {
                            throw new IllegalArgumentException("duplicate prompt key/version");
                        })));
    }

    /**
     * Finds a prompt by key and version.
     */
    public Optional<PromptDefinition> find(String key, int version) {
        return Optional.ofNullable(definitions.get(new PromptLookupKey(key, version)));
    }

    /**
     * Looks up a prompt by key and version.
     */
    public PromptDefinition get(String key, int version) {
        return find(key, version)
                .orElseThrow(() -> new IllegalArgumentException("prompt definition not found"));
    }

    /**
     * Looks up a prompt by full immutable identity.
     */
    public PromptDefinition get(PromptIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        var definition = get(identity.key(), identity.version());
        if (!definition.identity().hash().equals(identity.hash())) {
            throw new IllegalArgumentException("prompt hash mismatch");
        }
        return definition;
    }

    /**
     * Returns the latest prompt version for a key.
     */
    public PromptDefinition latest(String key) {
        return definitions.values().stream()
                .filter(definition -> definition.identity().key().equals(key))
                .max(Comparator.comparingInt(definition -> definition.identity().version()))
                .orElseThrow(() -> new IllegalArgumentException("prompt definition not found"));
    }

    private record PromptLookupKey(String key, int version) {

        private static PromptLookupKey from(PromptIdentity identity) {
            return new PromptLookupKey(identity.key(), identity.version());
        }
    }
}
