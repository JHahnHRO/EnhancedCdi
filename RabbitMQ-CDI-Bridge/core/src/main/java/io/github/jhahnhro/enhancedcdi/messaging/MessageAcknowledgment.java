package io.github.jhahnhro.enhancedcdi.messaging;

import java.io.IOException;

public interface MessageAcknowledgment {
    void ack() throws IOException;

    void reject(final boolean requeue) throws IOException;

}
