package io.github.jhahnhro.enhancedcdi.events;

import javax.enterprise.event.Event;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.EventMetadata;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Provider;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.Set;

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
public class EventMetadataImpl implements EventMetadata, Serializable {

    private final InjectionPoint eventInjectionPoint;
    private final Type eventType;
    private final Set<Annotation> qualifiers;

    /**
     * @param eventInjectionPoint injection point of the event.
     * @param eventType           payload type of the event.
     * @param qualifiers          qualifiers of the event.
     * @throws NullPointerException if any argument is null or if {@code qualifiers} contains null.
     */
    public EventMetadataImpl(InjectionPoint eventInjectionPoint, Type eventType, Set<Annotation> qualifiers) {
        this.eventInjectionPoint = Objects.requireNonNull(eventInjectionPoint);
        this.eventType = Objects.requireNonNull(eventType);
        this.qualifiers = Set.copyOf(qualifiers);
    }

    /**
     * @param eventInjectionPoint an injection point of type {@link Event Event<T>}.
     * @throws NullPointerException     if the injection point is null
     * @throws IllegalArgumentException if the injection point does not have a legal type for event injection points,
     *                                  i.e. {@code Event<T>}, {@code Instance<Event<T>>}, {@code Provider<Event<T>>},
     *                                  {@code Instance<Provider<Event<T>>}, etc. (all of which except the first are
     *                                  nonsensical for a real world application, but technically allowed by the CDI
     *                                  spec)
     */
    public EventMetadataImpl(InjectionPoint eventInjectionPoint) {
        this(Objects.requireNonNull(eventInjectionPoint), extractSpecifiedType(eventInjectionPoint),
             eventInjectionPoint.getQualifiers());
    }

    private static Type extractSpecifiedType(InjectionPoint injectionPoint) {
        Type type = injectionPoint.getType();
        if (type instanceof ParameterizedType pType) {
            Type eventType = extractEventType(pType);
            if (eventType != null) {
                return eventType;
            }
        }

        throw new IllegalArgumentException(injectionPoint + " is not a legal injection point for an Event");
    }

    private static Type extractEventType(final ParameterizedType parameterizedType) {
        ParameterizedType paramType = parameterizedType;
        while (true) {
            Type rawType = paramType.getRawType();
            if (rawType == Event.class) {
                return paramType.getActualTypeArguments()[0];
            } else if ((rawType == Provider.class || rawType == Instance.class)
                       && paramType.getActualTypeArguments()[0] instanceof ParameterizedType pType) {
                // It's not particular reasonable, but nevertheless perfectly legal to have an injection point of type
                // Instance<Event<T>> or Provider<Provider<Event<T>>> or Provider<Instance<Provider<...
                paramType = pType;
                continue;
            }
            return null;
        }
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

    @Override
    public String toString() {
        return "EventMetadata{type=" + eventType + ", qualifiers=" + qualifiers + ", injectionPoint="
               + eventInjectionPoint + "}";
    }
}