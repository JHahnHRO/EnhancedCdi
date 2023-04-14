package io.github.jhahnhro.enhancedcdi.messaging.serialization;

import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;

public class RabbitMqApplicationException extends RuntimeException {
    private final Outgoing.Response<byte[], Object> response;

    public RabbitMqApplicationException(Outgoing.Response<byte[], ?> response) {
        this.response = (Outgoing.Response<byte[], Object>) response;
    }

    public Outgoing.Response<byte[], Object> getResponse() {
        return response;
    }
}
