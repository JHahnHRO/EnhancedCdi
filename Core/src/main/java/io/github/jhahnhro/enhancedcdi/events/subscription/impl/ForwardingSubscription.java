package io.github.jhahnhro.enhancedcdi.events.subscription.impl;

import io.github.jhahnhro.enhancedcdi.events.subscription.Subscription;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Set;

class ForwardingSubscription<T> implements Subscription<T> {
    protected final Subscription<T> delegate;

    ForwardingSubscription(Subscription<T> delegate) {this.delegate = delegate;}

    @Override
    public void enable() {
        delegate.enable();
    }

    @Override
    public void disable() {
        delegate.disable();
    }

    @Override
    public boolean isEnabled() {
        return delegate.isEnabled();
    }

    @Override
    public void cancel() {
        delegate.cancel();
    }

    @Override
    public boolean isCancelled() {
        return delegate.isCancelled();
    }

    @Override
    public void deliver(T event) {
        delegate.deliver(event);
    }

    @Override
    public boolean supportsManualDelivery() {
        return delegate.supportsManualDelivery();
    }

    @Override
    public int getPriority() {
        return delegate.getPriority();
    }

    @Override
    public Type getObservedType() {
        return delegate.getObservedType();
    }

    @Override
    public Set<Annotation> getObservedQualifiers() {
        return delegate.getObservedQualifiers();
    }

}
