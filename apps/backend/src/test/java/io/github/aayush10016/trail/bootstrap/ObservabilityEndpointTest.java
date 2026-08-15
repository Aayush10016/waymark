package io.github.aayush10016.trail.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ObservabilityEndpointTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void shouldExposeMinimalHealthEndpoint() {
        var response = restTemplate.getForEntity("/actuator/health", HealthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("UP");
        assertThat(response.getBody().components()).isNull();
        assertThat(response.getBody().details()).isNull();
    }

    @Test
    void shouldExposeMinimalLivenessAndReadinessEndpoints() {
        var liveness = restTemplate.getForEntity("/actuator/health/liveness", HealthResponse.class);
        var readiness = restTemplate.getForEntity("/actuator/health/readiness", HealthResponse.class);

        assertThat(liveness.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readiness.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(liveness.getBody()).isEqualTo(new HealthResponse("UP", null, null, null));
        assertThat(readiness.getBody()).isEqualTo(new HealthResponse("UP", null, null, null));
    }

    @Test
    void shouldKeepMetricsAndHealthDetailsOutOfPublicActuatorExposure() {
        var metrics = restTemplate.getForEntity("/actuator/metrics", String.class);
        var healthPathDiscovery = restTemplate.getForEntity("/actuator", String.class);

        assertThat(metrics.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(healthPathDiscovery.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldRegisterMicrometerWithContentSafeCommonTags() {
        meterRegistry.counter("trail.observability.test").increment();

        var counter = meterRegistry.find("trail.observability.test").counter();

        assertThat(counter).isNotNull();
        assertThat(counter.getId().getTag("application")).isEqualTo("trail-backend");
        assertThat(counter.getId().getTags())
                .extracting(tag -> tag.getKey() + "=" + tag.getValue())
                .doesNotContain(
                        "email=developer@example.com",
                        "token=raw-refresh-token",
                        "url=https://internal.example.test/path",
                        "solution=Allowed localhost in CORS");
    }

    private record HealthResponse(
            String status, Map<String, Object> components, Map<String, Object> details, Object groups) {}
}
