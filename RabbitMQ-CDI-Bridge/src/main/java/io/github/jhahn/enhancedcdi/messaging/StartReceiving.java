package io.github.jhahn.enhancedcdi.messaging;

import java.util.Objects;

public record StartReceiving(String queue) {
    public StartReceiving {
        Objects.requireNonNull(queue);
    }

    // TODO: Consumer config, at least an enum auto-ack / ack after preprocessing / ack after processing
}
