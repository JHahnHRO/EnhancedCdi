package io.github.jhahnhro.enhancedcdi.messaging.impl;

import io.github.jhahnhro.enhancedcdi.messaging.MessageAcknowledgment;

/**
 * The singleton that does automatic acknowledgement.
 */
enum AutoAck implements MessageAcknowledgment {
    INSTANCE;

    @Override
    public void ack() {
        // no-op
    }

    @Override
    public void reject(boolean requeue) {
        // no-op
    }
}
