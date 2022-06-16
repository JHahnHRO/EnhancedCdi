package io.github.jhahnhro.enhancedcdi.events.subscription.impl;

import io.github.jhahnhro.enhancedcdi.events.subscription.Subscription;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.EventMetadata;
import javax.inject.Inject;
import javax.interceptor.Interceptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Set;
import java.util.function.BiConsumer;

@ApplicationScoped
public class EventDispatcher {

    @Inject
    private SubscriptionRegistry subscriptionRegistry;

    private <T> void dispatch(@Observes @Priority(Interceptor.Priority.LIBRARY_AFTER) T payload,
                              EventMetadata eventMetadata) {
        Type runtimeType = eventMetadata.getType();
        Set<Annotation> qualifiers = eventMetadata.getQualifiers();
        for (Subscription<?> subscription : subscriptionRegistry.getMatchingSubscriptions(runtimeType, qualifiers)) {
            notify((SubscriptionImpl<? super T>) subscription, payload, eventMetadata);
        }
    }

    private <T> void notify(SubscriptionImpl<? super T> subscription, T payload, EventMetadata eventMetadata) {
        BiConsumer<? super T, EventMetadata> callback = subscription.getCallback();
        if (callback != null) {
            // guard against race condition where a subscription has been cancelled after getMatchingSubscriptions()
            // was called.
            callback.accept(payload, eventMetadata);
        }
    }
}
