package io.github.jhahnhro.enhancedcdi.messaging;

import java.io.IOException;

public interface MessageAcknowledgment {
    void ack() throws IOException;

    void reject(final boolean requeue) throws IOException;

    enum Mode {
        AUTO, MANUAL
    }

    /**
     * A no-op implementation for consumers in auto-ack mode.
     */
    class AutoAck implements MessageAcknowledgment {

        @Override
        public void ack() {
            // no-op
        }

        @Override
        public void reject(boolean requeue) {
            // no-op
        }
    }
}
