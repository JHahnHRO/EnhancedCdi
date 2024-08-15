package io.github.jhahnhro.enhancedcdi.events;


import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.enterprise.inject.spi.EventMetadata;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.util.TypeLiteral;

/**
 * A base class for decorating the built-in bean of type(s) {@code Event<..>}. The default implementation simply
 * delegates {@link Event#fire(Object)} and {@link Event#fireAsync(Object)}. The only abstract method is
 * {@link #decorate(Event, EventMetadata)} which is needed to return a correctly decorated Event from the three
 * {@code select} methods.
 *
 * @param <T> the Event type
 */
public abstract class AbstractEventDecorator<T> implements Event<T>, Serializable {
    /**
     * The {@link Event} instance to which all calls are delegated in the end.
     */
    protected final Event<T> delegate;

    /**
     * The metadata describing the event, i.e. the injection point, the selected qualifiers and the selected event type
     * {@code U}.
     * <p>
     * The selected qualifiers can be a super set of {@code injectionPoint.getQualifiers()} and {@code U} can be a
     * subtype of {@code T} if the injection point has required type {@code Event<T>}) in cases when
     * {@link Event#select(Class, Annotation...)} was called to specialise the event.
     */
    protected final EventMetadata eventMetadata;


    /**
     * Constructs a decorator for the given delegate with the given event metadata. Subclasses typically will have a
     * matching constructor and override {@link #decorate(Event, EventMetadata)} to call that constructor.
     *
     * @param delegate      the {@link Event} instance to which all call are delegated in the end.
     * @param eventMetadata the event metadata consisting of the injection point, the selected type and selected
     *                      qualifiers of the Event instance.
     */
    protected AbstractEventDecorator(Event<T> delegate, EventMetadata eventMetadata) {
        this.delegate = delegate;
        this.eventMetadata = eventMetadata;
    }


    /**
     * Constructs a decorator for the given delegate injected into the given injection point. Useful as a bean
     * constructor.
     *
     * @param delegate       the {@link Event} instance to which all call are delegated in the end.
     * @param injectionPoint the injection point into which the Event instance was injected.
     */
    protected AbstractEventDecorator(Event<T> delegate, InjectionPoint injectionPoint) {
        this(delegate, new EventMetadataImpl(injectionPoint));
    }

    /**
     * Constructs a decorator for the given delegate with the given event metadata. Subclasses typically will have a
     * matching constructor and override this method to call that constructor.
     *
     * @param delegate      the {@link Event} instance to which all call are delegated in the end.
     * @param eventMetadata the event metadata consisting of the injection point, the selected type and selected
     *                      qualifiers of the Event instance.
     * @param <U>           the selected event type.
     * @return a decorated Event instance
     */
    protected abstract <U extends T> AbstractEventDecorator<U> decorate(Event<U> delegate, EventMetadata eventMetadata);

    @Override
    public void fire(T event) {
        delegate.fire(event);
    }

    @Override
    public <U extends T> CompletionStage<U> fireAsync(U event) {
        return delegate.fireAsync(event);
    }

    @Override
    public <U extends T> CompletionStage<U> fireAsync(U event, NotificationOptions options) {
        return delegate.fireAsync(event, options);
    }

    @Override
    public Event<T> select(Annotation... qualifiers) {
        return decorate(delegate.select(qualifiers), createNewMetadata(eventMetadata.getType(), qualifiers));
    }

    @Override
    public <U extends T> Event<U> select(Class<U> subtype, Annotation... qualifiers) {
        return decorate(delegate.select(subtype, qualifiers), createNewMetadata(subtype, qualifiers));
    }

    @Override
    public <U extends T> Event<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
        return decorate(delegate.select(subtype, qualifiers), createNewMetadata(subtype.getType(), qualifiers));
    }

    /**
     * Creates a new {@link EventMetadata} instance with the same {@link InjectionPoint} as {@link #eventMetadata}, but
     * a different type and possible more qualifiers.
     *
     * @param subtype              the new type
     * @param additionalQualifiers additional qualifiers
     * @return a new {@link EventMetadata} instance
     */
    protected EventMetadata createNewMetadata(Type subtype, Annotation... additionalQualifiers) {
        Set<Annotation> newQualifiers = new LinkedHashSet<>(eventMetadata.getQualifiers());
        Collections.addAll(newQualifiers, additionalQualifiers);

        return new EventMetadataImpl(eventMetadata.getInjectionPoint(), subtype, newQualifiers);
    }
}
