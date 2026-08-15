package io.github.aayush10016.trail.intelligence.domain.port;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class AIProviderPortTest {

    @Test
    void portDoesNotExposeKnownProviderSdkTypes() {
        var typeNames = Arrays.stream(AIProvider.class.getMethods())
                .flatMap(method -> Arrays.stream(method.getParameterTypes()))
                .map(Class::getName)
                .toList();

        assertThat(typeNames)
                .allSatisfy(typeName -> assertThat(typeName)
                        .doesNotContain("openai")
                        .doesNotContain("anthropic")
                        .doesNotContain("google")
                        .doesNotContain("gemini"));
    }
}
