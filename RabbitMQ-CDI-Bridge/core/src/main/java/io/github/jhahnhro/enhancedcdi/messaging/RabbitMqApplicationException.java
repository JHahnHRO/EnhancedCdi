package io.github.jhahnhro.enhancedcdi.messaging;

import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;

/**
 * Exceptions of this type can be thrown by application code to indicate that an error has occurred while handling an
 * incoming message that requires a response message to be sent. Typically, this is necessary to handle exceptions from
 * {@link io.github.jhahnhro.enhancedcdi.messaging.rpc.RpcEndpoint RPC endpoint methods}.
 */
public class RabbitMqApplicationException extends RuntimeException {
    private final Outgoing.Response<byte[], Object> response;

    /**
     * Creates a new instance
     *
     * @param response the appropriate response
     */
    public RabbitMqApplicationException(Outgoing.Response<byte[], ?> response) {
        this.response = (Outgoing.Response<byte[], Object>) response;
    }

    public Outgoing.Response<byte[], Object> getResponse() {
        return response;
    }
}
