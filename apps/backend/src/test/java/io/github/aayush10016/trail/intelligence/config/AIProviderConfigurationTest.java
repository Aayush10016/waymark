package io.github.aayush10016.trail.intelligence.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aayush10016.trail.intelligence.adapter.out.ai.MockAIProvider;
import io.github.aayush10016.trail.intelligence.adapter.out.ai.OpenAICompatibleAIProvider;
import io.github.aayush10016.trail.intelligence.domain.port.AIProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AIProviderConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class)
            .withUserConfiguration(AIProviderConfiguration.class);

    @Test
    void selectsMockProviderByDefault() {
        contextRunner.run(context -> assertThat(context.getBean(AIProvider.class)).isInstanceOf(MockAIProvider.class));
    }

    @Test
    void selectsOpenAIProviderWhenConfigured() {
        contextRunner
                .withPropertyValues(
                        "trail.ai.provider=openai",
                        "trail.ai.openai.api-key=test-api-key",
                        "trail.ai.openai.timeout=3s")
                .run(context -> assertThat(context.getBean(AIProvider.class))
                        .isInstanceOf(OpenAICompatibleAIProvider.class));
    }

    @Test
    void rejectsOpenAISelectionWithoutApiKey() {
        contextRunner
                .withPropertyValues("trail.ai.provider=openai")
                .run(context -> assertThat(context).hasFailed());
    }
}
