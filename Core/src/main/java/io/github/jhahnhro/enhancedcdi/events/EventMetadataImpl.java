package io.github.jhahnhro.enhancedcdi.events;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import io.github.jhahnhro.enhancedcdi.types.TypeVariableResolver;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.spi.EventMetadata;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Provider;

/**
 * A relatively simple implementation of {@link EventMetadata} because the CDI spec does not provide a default one.
 * <p>
 * Only notable feature is the constructor {@link EventMetadataImpl#EventMetadataImpl(InjectionPoint)} that extracts the
 * type and qualifiers directly from an injection point of type {@link Event Event<T>}.
 */
// Sonar and IntelliJ want the fields in this class to be Serializable. What they don't know: All the
// possible values that these fields can have, are serializable at runtime as guaranteed by the CDI spec, even though
// the various classes do not implement the Serializable interface, e.g. InjectionPoint is a built-in bean that is
// required by the spec to be serializable.
@SuppressWarnings("java:S1948")
public record EventMetadataImpl(InjectionPoint eventInjectionPoint, Type eventType, Set<Annotation> qualifiers)
        implements EventMetadata, Serializable {

    /**
     * @param eventInjectionPoint injection point of the event.
     * @param eventType           payload type of the event.
     * @param qualifiers          qualifiers of the event.
     * @throws NullPointerException if any argument is null or if {@code qualifiers} contains null.
     */
    public EventMetadataImpl {
        Objects.requireNonNull(eventInjectionPoint);
        Objects.requireNonNull(eventType);
        qualifiers = Set.copyOf(qualifiers);
    }

    /**
     * Convenience constructor that constructs a EventMetaData from an injection point of type {@link Event Event<T>}.
     *
     * @param eventInjectionPoint an injection point of type {@link Event Event<T>}.
     * @throws NullPointerException     if the injection point is {@code null}
     * @throws IllegalArgumentException if the injection point does not have a legal type for event injection points,
     *                                  i.e. {@code Event<T>}, {@code WeldEvent<T>}, {@code EnhancedEvent<T>},
     *                                  {@code Instance<Event<T>>}, {@code Provider<Event<T>>},
     *                                  {@code Instance<Provider<Event<T>>}, etc. (those last three are nonsensical for
     *                                  a real world application, but technically allowed by the CDI spec)
     */
    public EventMetadataImpl(InjectionPoint eventInjectionPoint) {
        this(eventInjectionPoint, extractSpecifiedType(eventInjectionPoint), eventInjectionPoint.getQualifiers());
    }

    private static Type extractSpecifiedType(InjectionPoint injectionPoint) {
        Type type = injectionPoint.getType();
        if (type instanceof ParameterizedType parameterizedType) {
            if (parameterizedType.getRawType() == Event.class) { // shortcut for the most common case
                return parameterizedType.getActualTypeArguments()[0];
            }
            // more complex cases: Maybe it's WeldEvent<T>. We must resolve type variables.
            Type eventType = extractEventType(parameterizedType,
                                              TypeVariableResolver.withKnownTypesOf(parameterizedType));
            if (eventType != null) {
                return eventType;
            }
        }

        throw new IllegalArgumentException(injectionPoint + " is not a legal injection point for an Event");
    }

    private static Type extractEventType(final ParameterizedType parameterizedType, TypeVariableResolver resolver) {
        ParameterizedType paramType = parameterizedType;

        // It's not particular reasonable, but nevertheless perfectly legal to have an injection point of
        // type Instance<Event<T>> or Provider<Provider<Event<T>>> or Provider<Instance<Provider<...
        //
        // 1. Step: Unpack those types
        Set<Type> alreadyVisited = new HashSet<>();
        while (Provider.class.isAssignableFrom((Class<?>) paramType.getRawType())) {
            if (!alreadyVisited.add(paramType)) {
                // Parametrized types can have cycles!! A self-referential definition like
                // class Foobar implements Provider<Foobar>{...}
                // can exist as a type. But no such types are types of Event injection points.
                return null;
            }

            final ParameterizedType resolvedArgument = (ParameterizedType) resolver.resolve(Provider.class);
            if (resolvedArgument.getActualTypeArguments()[0] instanceof ParameterizedType pType) {
                resolver = TypeVariableResolver.withKnownTypesOf(pType);
                paramType = pType;
            }
        }

        // 2. Step: Unpack the event type
        if (Event.class.isAssignableFrom((Class<?>) paramType.getRawType())) {
            return resolver.resolve(Event.class.getTypeParameters()[0]);
        }
        return null;
    }

    @Override
    public InjectionPoint getInjectionPoint() {
        return eventInjectionPoint;
    }

    @Override
    public Type getType() {
        return eventType;
    }

    @Override
    public Set<Annotation> getQualifiers() {
        return qualifiers;
    }
}