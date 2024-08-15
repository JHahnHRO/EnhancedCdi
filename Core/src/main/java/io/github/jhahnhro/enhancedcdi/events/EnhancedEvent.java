package io.github.jhahnhro.enhancedcdi.events;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Set;

import io.github.jhahnhro.enhancedcdi.metadata.InjectionPointImpl;
import io.github.jhahnhro.enhancedcdi.types.ParameterizedTypeImpl;
import io.github.jhahnhro.enhancedcdi.types.TypeVariableResolver;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Typed;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.EventMetadata;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Inject;

/**
 * Helps with the lack of {@code select(Type, Annotation...)} in {@link jakarta.enterprise.inject.Instance}
 */
@Dependent
@Typed(EnhancedEvent.class)
public final class EnhancedEvent<T> extends AbstractEventDecorator<T> {
    private final BeanManager beanManager;

    @Inject
    EnhancedEvent(BeanManager beanManager, InjectionPoint injectionPoint) {
        this(beanManager, new EventMetadataImpl(injectionPoint));
    }

    private EnhancedEvent(BeanManager beanManager, EventMetadata eventMetadata) {
        this(beanManager, getEventUnchecked(beanManager, eventMetadata), eventMetadata);
    }

    private EnhancedEvent(BeanManager beanManager, Event<T> delegate, EventMetadata eventMetadata) {
        super(delegate, eventMetadata);
        this.beanManager = beanManager;
    }

    private static ParameterizedType eventTypeOf(Type type) {
        return new ParameterizedTypeImpl(Event.class, null, type);
    }

    private static <U> Event<U> getEventUnchecked(BeanManager bm, EventMetadata eventMetadata) {
        var type = eventTypeOf(eventMetadata.getType());
        var qualifiers = eventMetadata.getQualifiers();
        var injectionPoint = InjectionPointImpl.mutate(eventMetadata.getInjectionPoint(), type, qualifiers);
        //noinspection unchecked
        return (Event<U>) bm.getInjectableReference(injectionPoint, bm.createCreationalContext(null));
    }

    @Override
    public EnhancedEvent<T> select(Annotation... qualifiers) {
        return (EnhancedEvent<T>) super.select(qualifiers);
    }

    @Override
    public <U extends T> EnhancedEvent<U> select(Class<U> subtype, Annotation... qualifiers) {
        return (EnhancedEvent<U>) super.select(subtype, qualifiers);
    }

    @Override
    public <U extends T> EnhancedEvent<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
        return (EnhancedEvent<U>) super.select(subtype, qualifiers);
    }

    /**
     * Obtains a child <code>Event</code> for the given required type and additional required qualifiers. Unlike
     * {@code select()}, this method also works with types that are dynamically created at runtime.
     *
     * @param <U>        the specified type
     * @param subtype    a {@link Type} representing the specified type
     * @param qualifiers the additional specified qualifiers
     * @return the child {@code EnhancedEvent}
     * @throws IllegalArgumentException if the typ is not a subtype of the current event type or if the qualifiers
     *                                  contain two instances of the same non-repeating qualifier type, or an instance
     *                                  of an annotation that is not a qualifier type
     */
    public <U extends T> EnhancedEvent<U> selectUnchecked(final Type subtype, Annotation... qualifiers) {
        delegate.select(qualifiers); // throws IllegalArgumentException when illegal qualifiers are used

        Set<Type> types = TypeVariableResolver.withKnownTypesOf(subtype).resolvedTypeClosure(subtype);
        if (!types.contains(this.eventMetadata.getType())) {
            throw new IllegalArgumentException(subtype + " is not a subtype of " + this.eventMetadata.getType());
        }

        return new EnhancedEvent<>(beanManager, createNewMetadata(subtype, qualifiers));
    }

    @Override
    protected <U extends T> EnhancedEvent<U> decorate(Event<U> delegate, EventMetadata eventMetadata) {
        return new EnhancedEvent<>(beanManager, delegate, eventMetadata);
    }
}