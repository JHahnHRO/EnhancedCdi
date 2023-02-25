package io.github.jhahnhro.enhancedcdi.messaging.impl;

import io.github.jhahnhro.enhancedcdi.messaging.messages.Acknowledgment;

/**
 * The singleton that does automatic acknowledgement.
 */
enum AutoAck implements Acknowledgment {
    INSTANCE;

    @Override
    public void ack() {
        // no-op
    }

    @Override
    public void reject(boolean requeue) {
        throw new IllegalStateException("Message cannot be rejected, because it was received in auto-ack mode");
    }

    @Override
    public State getState() {
        return State.ACKNOWLEDGED;
    }
}
