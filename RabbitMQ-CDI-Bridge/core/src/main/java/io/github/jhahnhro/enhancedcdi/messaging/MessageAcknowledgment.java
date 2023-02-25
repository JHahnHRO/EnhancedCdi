package io.github.jhahnhro.enhancedcdi.messaging;

import java.io.IOException;

/**
 * Allows to acknowledge or reject a RabbitMQ message to the broker.
 */
public interface MessageAcknowledgment {
    /**
     * Acknowledges the message if not already acknowledged. Does nothing if it has been acknowledged before. Throws
     * {@link IllegalStateException} if it has been rejected before.
     *
     * @throws IOException if something goes wrong during acknowledgement
     */
    void ack() throws IOException;

    /**
     * Rejects the message if not already rejected. Does nothing if it has been rejected before. Throws
     * {@link IllegalStateException} if it has been acknowledged before.
     *
     * @param requeue whether to requeue the message on the broker
     * @throws IOException if something goes wrong during rejection
     */
    void reject(final boolean requeue) throws IOException;

    /**
     * @return Current state of the message.
     */
    State getState();

    enum State {
        /**
         * Message has neither been acknowledged nor rejected yet.
         */
        UNACKNOWLEDGED,
        /**
         * Message has been acknowledged.
         */
        ACKNOWLEDGED,
        /**
         * Message has been rejected.
         */
        REJECTED
    }
}
