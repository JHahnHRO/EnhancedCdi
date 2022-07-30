package io.github.jhahnhro.enhancedcdi.events.subscription.impl;

import io.github.jhahnhro.enhancedcdi.events.subscription.Subscription;

import javax.enterprise.inject.spi.EventMetadata;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

class SubscriptionImpl<T> implements Subscription<T> {
    private final Type observedEventType;
    private final Set<Annotation> qualifiers;
    private final int priority;
    private final String name;
    private final boolean supportsManualDelivery;

    private boolean enabled;
    private BiConsumer<T, EventMetadata> callback;

    SubscriptionImpl(AbstractSubscriptionBuilder<T> builder) {
        this.qualifiers = Set.copyOf(builder.qualifiers);
        this.observedEventType = Objects.requireNonNull(builder.observedEventType);
        this.priority = builder.priority;
        this.name = builder.name;

        this.callback = Objects.requireNonNull(builder.callback, "No callback was set");
        this.supportsManualDelivery = builder.supportsManualDelivery;

        this.enabled = false;
    }

    @Override
    public void enable() {
        synchronized (this) {
            if (isCancelled()) {
                throw new IllegalStateException("Subscription is cancelled.");
            }
            this.enabled = true;
        }
    }

    @Override
    public void disable() {
        synchronized (this) {
            this.enabled = false;
        }
    }

    @Override
    public boolean isEnabled() {
        synchronized (this) {
            return enabled;
        }
    }

    @Override
    public void cancel() {
        synchronized (this) {
            disable();
            callback = null;
        }
    }

    @Override
    public boolean isCancelled() {
        synchronized (this) {
            return callback == null;
        }
    }

    @Override
    public void deliver(T event) {
        Objects.requireNonNull(event);

        if (!supportsManualDelivery) {
            throw new UnsupportedOperationException("Events cannot be manually delivered to this subscription");
        }
        BiConsumer<T, EventMetadata> theCallback;
        synchronized (this) {
            theCallback = this.callback;
        }
        if (theCallback != null) {
            theCallback.accept(event, null);
        } else {
            throw new IllegalStateException("Event cannot be delivered, because the subscription is cancelled.");
        }
    }

    @Override
    public boolean supportsManualDelivery() {
        return supportsManualDelivery;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public Type getObservedType() {
        return observedEventType;
    }

    @Override
    public Set<Annotation> getObservedQualifiers() {
        return qualifiers;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "Subscription " + Objects.requireNonNullElse(name, "") + "[observes " + getObservedType().getTypeName()
               + " with qualifiers " + getObservedQualifiers() + "]";
    }

    BiConsumer<T, EventMetadata> getCallback() {
        synchronized (this) {
            return callback;
        }
    }
}
