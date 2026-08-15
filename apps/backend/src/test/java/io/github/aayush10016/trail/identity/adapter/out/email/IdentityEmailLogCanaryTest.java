package io.github.aayush10016.trail.identity.adapter.out.email;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.aayush10016.trail.identity.domain.model.EmailAddress;
import io.github.aayush10016.trail.identity.domain.model.EmailDeliveryMessage;
import io.github.aayush10016.trail.identity.domain.model.EmailDeliveryTemplate;
import io.github.aayush10016.trail.identity.domain.port.EmailDeliveryException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class IdentityEmailLogCanaryTest {

    private static final String EMAIL_CANARY = "developer@example.com";
    private static final String TOKEN_CANARY = "raw-reset-token-CANARY-123";
    private static final String LINK_CANARY = "https://app.trail.test/reset?token=raw-reset-token-CANARY-123";
    private static final String BODY_CANARY = "password body canary";

    @Test
    void localEmailDeliveryLogsDoNotContainRecipientsTokensLinksOrBodies() {
        var logger = (Logger) LoggerFactory.getLogger(LocalEmailDeliveryAdapter.class);
        var appender = attachListAppender(logger);
        var adapter = new LocalEmailDeliveryAdapter();

        adapter.send(
                new EmailDeliveryMessage(
                        EmailAddress.from(EMAIL_CANARY),
                        EmailDeliveryTemplate.RESET_PASSWORD,
                        "Reset " + TOKEN_CANARY,
                        BODY_CANARY + " " + LINK_CANARY,
                        "<a href=\"" + LINK_CANARY + "\">" + BODY_CANARY + "</a>"));

        var logs = renderedLogs(appender);

        assertThat(logs).contains("template=RESET_PASSWORD");
        assertThat(logs)
                .doesNotContain(EMAIL_CANARY)
                .doesNotContain(TOKEN_CANARY)
                .doesNotContain(LINK_CANARY)
                .doesNotContain(BODY_CANARY);
    }

    @Test
    void localEmailDeliveryFailureLogsDoNotContainExceptionMessageCanaries() {
        var logger = (Logger) LoggerFactory.getLogger(LocalEmailDeliveryAdapter.class);
        var appender = attachListAppender(logger);
        var adapter = new LocalEmailDeliveryAdapter();
        adapter.failWith(EmailDeliveryException.retryable("retry " + TOKEN_CANARY, null));

        try {
            adapter.send(
                    new EmailDeliveryMessage(
                        EmailAddress.from(EMAIL_CANARY),
                        EmailDeliveryTemplate.VERIFY_EMAIL,
                        "Verify " + TOKEN_CANARY,
                        BODY_CANARY + " " + LINK_CANARY,
                        "<a href=\"" + LINK_CANARY + "\">" + BODY_CANARY + "</a>"));
        } catch (EmailDeliveryException ignored) {
            // Expected for this log-canary test.
        }

        var logs = renderedLogs(appender);

        assertThat(logs).contains("template=VERIFY_EMAIL").contains("retryable=true");
        assertThat(logs)
                .doesNotContain(EMAIL_CANARY)
                .doesNotContain(TOKEN_CANARY)
                .doesNotContain(LINK_CANARY)
                .doesNotContain(BODY_CANARY);
    }

    private static ListAppender<ILoggingEvent> attachListAppender(Logger logger) {
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
        return appender;
    }

    private static String renderedLogs(ListAppender<ILoggingEvent> appender) {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + "\n" + right);
    }
}
