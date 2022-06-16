package io.github.jhahnhro.enhancedcdi.events.subscription.impl;

import io.github.jhahnhro.enhancedcdi.events.subscription.Subscription;

import javax.enterprise.inject.spi.EventMetadata;
import javax.enterprise.inject.spi.ObserverMethod;
import javax.enterprise.util.TypeLiteral;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

class AbstractSubscriptionBuilder<T> implements Subscription.Builder<T> {
    private static final BiConsumer<?, EventMetadata> NOOP_CALLBACK = (payload, metaData) -> {};

    Set<Annotation> qualifiers = new HashSet<>();
    Type observedEventType = Object.class;
    BiConsumer<T, EventMetadata> callback = (BiConsumer<T, EventMetadata>) NOOP_CALLBACK;
    boolean supportsManualDelivery = false;
    int priority = ObserverMethod.DEFAULT_PRIORITY;
    String name = null;

    @Override
    public Subscription.Builder<T> setQualifiers(Annotation... additionalQualifiers) {
        this.qualifiers.clear();
        this.qualifiers.addAll(List.of(Objects.requireNonNull(additionalQualifiers)));
        return this;
    }

    @Override
    public <U> Subscription.Builder<U> setType(Class<U> observedEventType) {
        if (this.callback != null) {
            throw new IllegalStateException("Callback was already set");
        }
        this.observedEventType = Objects.requireNonNull(observedEventType);
        return (Subscription.Builder<U>) this;
    }

    @Override
    public <U> Subscription.Builder<U> setType(TypeLiteral<U> observedEventType) {
        if (this.callback != null) {
            throw new IllegalStateException("Callback was already set");
        }
        this.observedEventType = Objects.requireNonNull(observedEventType).getType();
        return (Subscription.Builder<U>) this;
    }

    @Override
    public Subscription.Builder<T> setEventConsumer(Consumer<T> callback) {
        Objects.requireNonNull(callback, "Callback must not be null");
        this.callback = (payload, metadata) -> callback.accept(payload);
        this.supportsManualDelivery = true;
        return this;
    }

    @Override
    public Subscription.Builder<T> setEventConsumer(BiConsumer<T, EventMetadata> callback) {
        this.callback = Objects.requireNonNull(callback, "Callback must not be null");
        this.supportsManualDelivery = false;
        return this;
    }

    @Override
    public Subscription.Builder<T> setName(String name) {
        this.name = Objects.requireNonNull(name, "Subscription name must not be null");
        return this;
    }

    @Override
    public Subscription.Builder<T> setPriority(int priority) {
        this.priority = priority;
        return this;
    }

    @Override
    public Subscription<T> build() {
        if (callback == NOOP_CALLBACK) {
            throw new IllegalStateException("No callback has been set");
        }
        return new SubscriptionImpl<>(this);
    }

}
