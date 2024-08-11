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

import io.github.jhahnhro.enhancedcdi.metadata.InjectionPointImpl;
import io.github.jhahnhro.enhancedcdi.types.ParameterizedTypeImpl;

/**
 * Helps with the lack of {@code select(Type, Annotation...)} in {@link jakarta.enterprise.inject.Instance}
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
        ParameterizedType type = eventTypeOf(eventMetadata.getType());
        Set<Annotation> qualifiers = eventMetadata.getQualifiers();
        InjectionPoint ip = eventMetadata.getInjectionPoint();

        InjectionPoint syntheticInjectionPoint;
        if (ip == null) {
            syntheticInjectionPoint = new InjectionPointImpl(type, qualifiers);
        } else {
            syntheticInjectionPoint = new InjectionPointImpl(type, qualifiers, ip.getBean(), ip.getMember(),
                                                             ip.getAnnotated(), ip.isDelegate(), ip.isTransient());
        }
        //noinspection unchecked
        return (Event<U>) bm.getInjectableReference(syntheticInjectionPoint, bm.createCreationalContext(null));
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
}