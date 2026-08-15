package io.github.aayush10016.trail.identity.adapter.out.email;

import io.github.aayush10016.trail.identity.domain.model.EmailDeliveryMessage;
import io.github.aayush10016.trail.identity.domain.port.EmailDeliveryException;
import io.github.aayush10016.trail.identity.domain.port.EmailDeliveryPort;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Local email delivery adapter for development and tests.
 *
 * <p>The adapter records messages in memory and logs only template-level
 * metadata. It never logs recipients, subjects, bodies, tokens, or links.
 */
public final class LocalEmailDeliveryAdapter implements EmailDeliveryPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalEmailDeliveryAdapter.class);

    private final CopyOnWriteArrayList<EmailDeliveryMessage> deliveredMessages = new CopyOnWriteArrayList<>();
    private final AtomicReference<EmailDeliveryException> configuredFailure = new AtomicReference<>();

    @Override
    public void send(EmailDeliveryMessage message) {
        var safeMessage = Objects.requireNonNull(message, "message");
        var failure = configuredFailure.get();
        if (failure != null) {
            LOGGER.warn(
                    "Local identity email delivery failed template={} retryable={}",
                    safeMessage.template(),
                    failure.retryable());
            throw failure;
        }

        deliveredMessages.add(safeMessage);
        LOGGER.info("Local identity email delivery accepted template={}", safeMessage.template());
    }

    /**
     * Configures the adapter to fail future deliveries.
     *
     * @param failure typed delivery failure
     */
    public void failWith(EmailDeliveryException failure) {
        configuredFailure.set(Objects.requireNonNull(failure, "failure"));
    }

    /**
     * Clears a previously configured failure.
     */
    public void clearFailure() {
        configuredFailure.set(null);
    }

    /**
     * Returns delivered messages.
     *
     * @return immutable snapshot of delivered messages
     */
    public List<EmailDeliveryMessage> deliveredMessages() {
        return List.copyOf(deliveredMessages);
    }
}
