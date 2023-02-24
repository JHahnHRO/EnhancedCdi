package io.github.jhahnhro.enhancedcdi.messaging.rpc;

public class RpcNotActiveException extends IllegalStateException {
    public RpcNotActiveException(String message) {
        super(message);
    }

    public RpcNotActiveException(String message, Throwable cause) {
        super(message, cause);
    }

    public RpcNotActiveException(Throwable cause) {
        super(cause);
    }
}