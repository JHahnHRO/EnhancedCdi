package io.github.jhahnhro.enhancedcdi.messaging.impl;

public class RuntimeIOException extends RuntimeException {

    public RuntimeIOException(Throwable cause) {
        super(cause.getMessage(), cause);
    }
}
