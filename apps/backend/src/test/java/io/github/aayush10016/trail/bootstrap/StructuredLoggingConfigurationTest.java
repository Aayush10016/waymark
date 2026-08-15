package io.github.aayush10016.trail.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

@SpringBootTest
class StructuredLoggingConfigurationTest {

    @Autowired
    private Environment environment;

    @Test
    void shouldEnableEcsStructuredConsoleLogging() {
        assertThat(environment.getProperty("logging.structured.format.console")).isEqualTo("ecs");
    }
}
