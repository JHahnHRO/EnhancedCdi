package io.github.jhahnhro.enhancedcdi.events.subscription.impl;

import io.github.jhahnhro.enhancedcdi.events.subscription.Subscription;
import io.github.jhahnhro.enhancedcdi.types.TypeVariableResolver;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Any;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@ApplicationScoped
class SubscriptionRegistry {

    private final Collection<Subscription<?>> subscriptions = new CopyOnWriteArrayList<>();

    Collection<Subscription<?>> getMatchingSubscriptions(Type runtimeType, Set<Annotation> qualifiers) {

        TypeVariableResolver resolver = TypeVariableResolver.withKnownTypesOf(runtimeType);
        Set<Type> allPayloadTypes = resolver.resolvedTypeClosure(runtimeType);

        Set<Annotation> runtimeQualifiers = new HashSet<>(qualifiers);
        runtimeQualifiers.add(Any.Literal.INSTANCE);

        return subscriptions.stream()
                .filter(Subscription::isEnabled)
                .filter(sub -> runtimeQualifiers.containsAll(sub.getObservedQualifiers()))
                .filter(sub -> matchesType(sub.getObservedType(), allPayloadTypes))
                .sorted(Subscription.PRIORITY_COMPARATOR)
                .collect(Collectors.toList());
    }

    private boolean matchesType(Type type, Set<Type> allPayloadTypes) {
        return false;
    }

    void register(Subscription<?> subscription) {
        this.subscriptions.add(Objects.requireNonNull(subscription));
    }

    void unregister(Subscription<?> subscription) {
        this.subscriptions.remove(subscription);
    }

}
