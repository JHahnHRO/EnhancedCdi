package io.github.jhahnhro.enhancedcdi.events;

import java.util.Objects;

import jakarta.enterprise.inject.spi.EventContext;
import jakarta.enterprise.inject.spi.EventMetadata;

/**
 * A simple implementation of {@link EventContext} because the CDI spec does not provide a default one.
 */
public record EventContextImpl<T>(T event, EventMetadata metadata) implements EventContext<T> {

    public EventContextImpl {
        Objects.requireNonNull(event);
        Objects.requireNonNull(metadata);
    }

    @Override
    public T getEvent() {
        return event;
    }

    @Override
    public EventMetadata getMetadata() {
        return metadata;
    }
}
