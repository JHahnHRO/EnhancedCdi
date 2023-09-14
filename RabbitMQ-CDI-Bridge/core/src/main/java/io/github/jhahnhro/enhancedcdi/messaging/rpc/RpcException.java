package io.github.jhahnhro.enhancedcdi.messaging.rpc;

public class RpcException extends RuntimeException {
    public RpcException(String message) {
        super(message);
    }

    public RpcException(Throwable cause) {
        super(cause);
    }
}
