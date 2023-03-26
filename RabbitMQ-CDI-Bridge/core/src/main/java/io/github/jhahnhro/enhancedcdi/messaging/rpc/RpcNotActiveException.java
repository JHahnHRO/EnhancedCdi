package io.github.jhahnhro.enhancedcdi.messaging.rpc;

public class RpcNotActiveException extends RpcException {
    public RpcNotActiveException() {
        super("No RabbitMQ request has been received in the current RequestScope");
    }
}
