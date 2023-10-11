package io.github.jhahnhro.enhancedcdi.messaging.serialization;

public class SerializationException extends RuntimeException {
    public SerializationException(Exception cause) {
        this("Outgoing message could not be serialized", cause);
    }

    public SerializationException(String message) {
        super(message);
    }

    public SerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
