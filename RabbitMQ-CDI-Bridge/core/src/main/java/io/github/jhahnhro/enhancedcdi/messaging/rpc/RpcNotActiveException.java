package io.github.jhahnhro.enhancedcdi.messaging.rpc;

public class RpcNotActiveException extends IllegalStateException {
    public RpcNotActiveException(String message) {
        super(message);
    }
}
