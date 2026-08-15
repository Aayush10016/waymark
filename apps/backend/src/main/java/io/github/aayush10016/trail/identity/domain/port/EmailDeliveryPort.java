package io.github.aayush10016.trail.identity.domain.port;

import io.github.aayush10016.trail.identity.domain.model.EmailDeliveryMessage;

/**
 * Outbound port for identity email delivery.
 */
public interface EmailDeliveryPort {

    /**
     * Sends an identity email message.
     *
     * @param message message to deliver
     * @throws EmailDeliveryException when delivery fails
     */
    void send(EmailDeliveryMessage message);
}
