package io.github.jhahn.enhancedcdi.messaging.serialization;

import java.lang.reflect.Type;

public record Deserialized<T>(T message, Type type) {
    public Deserialized(T message) {
        this(message, message.getClass());
    }
}
