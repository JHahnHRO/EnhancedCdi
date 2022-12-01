package io.github.jhahnhro.enhancedcdi.events;

import io.github.jhahnhro.enhancedcdi.types.ParameterizedTypeImpl;

import javax.enterprise.context.Dependent;
import javax.enterprise.event.Event;
import javax.enterprise.inject.Typed;
import javax.enterprise.inject.spi.*;
import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.lang.reflect.Member;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Helps with the lack of {@code select(Type, Annotation...)} in {@link javax.enterprise.inject.Instance}
 */
@Dependent
@Typed(EnhancedEvent.class)
public class EnhancedEvent<T> extends AbstractEventDecorator<T> {
    final BeanManager beanManager;

    EnhancedEvent(BeanManager beanManager, Event<T> delegate, EventMetadata eventMetadata) {
        super(delegate, eventMetadata);
        this.beanManager = beanManager;
    }

    protected EnhancedEvent(BeanManager beanManager, EventMetadata eventMetadata) {
        this(beanManager, getEventUnchecked(beanManager, eventMetadata), eventMetadata);
    }

    @Inject
    protected EnhancedEvent(BeanManager beanManager, InjectionPoint injectionPoint) {
        this(beanManager,
             new EventMetadataImpl(injectionPoint, typeArgumentOf(injectionPoint), injectionPoint.getQualifiers()));
    }

    private static Type typeArgumentOf(InjectionPoint injectionPoint) {
        return ((ParameterizedType) injectionPoint.getType()).getActualTypeArguments()[0];
    }

    private static ParameterizedType eventTypeOf(Type type) {
        return new ParameterizedTypeImpl(Event.class, null, type);
    }

    private static <U> Event<U> getEventUnchecked(BeanManager bm, EventMetadata eventMetadata) {
        ParameterizedType eventType = eventTypeOf(eventMetadata.getType());
        SyntheticInjectionPoint ij = new SyntheticInjectionPoint(eventType, eventMetadata.getQualifiers());
        //noinspection unchecked
        return (Event<U>) bm.getInjectableReference(ij, bm.createCreationalContext(null));
    }

    @Override
    protected <U extends T> EnhancedEvent<U> decorate(Event<U> delegate, EventMetadata eventMetadata) {
        return new EnhancedEvent<>(beanManager, delegate, eventMetadata);
    }

    public <U> EnhancedEvent<U> selectUnchecked(final Type type, Annotation... qualifiers) {
        final Set<Annotation> newQualifiers = new HashSet<>(eventMetadata.getQualifiers());
        Collections.addAll(newQualifiers, qualifiers);

        EventMetadata newEventMetadata = new EventMetadataImpl(eventMetadata.getInjectionPoint(), type, newQualifiers);
        return new EnhancedEvent<>(beanManager, newEventMetadata);
    }

    private record SyntheticInjectionPoint(Type type, Set<Annotation> qualifiers) implements InjectionPoint {

        @Override
        public Type getType() {
            return type;
        }

        @Override
        public Set<Annotation> getQualifiers() {
            return qualifiers;
        }

        @Override
        public Bean<?> getBean() {
            return null;
        }

        @Override
        public Member getMember() {
            return null;
        }

        @Override
        public Annotated getAnnotated() {
            return null;
        }

        @Override
        public boolean isDelegate() {
            return false;
        }

        @Override
        public boolean isTransient() {
            return false;
        }
    }
}