package io.github.jhahnhro.enhancedcdi.messaging.serialization;

public class DeserializationException extends RuntimeException {
    public DeserializationException(Exception cause) {
        super("Incoming message could not be de-serialized", cause);
    }
}
