package io.github.jhahnhro.enhancedcdi.events;

import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.EventContext;
import javax.enterprise.inject.spi.EventMetadata;
import javax.enterprise.util.AnnotationLiteral;
import javax.enterprise.util.TypeLiteral;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@ExtendWith(WeldJunit5Extension.class)
class EnhancedEventTest {

    @WeldSetup
    WeldInitiator weld = WeldInitiator.from(EnhancedEvent.class, Observer.class, TestQualifier.class).build();
    @Inject
    EnhancedEvent<Object> enhancedEvent;

    @Inject
    Observer observer;

    public static Stream<Arguments> getArguments() {
        final Annotation[] none = new Annotation[0];
        final Annotation[] qualifier = new Annotation[]{new AnnotationLiteral<TestQualifier>() {}};
        return Stream.of(
                //@formatter:off
                arguments(new TypeLiteral<Integer>() {}, none, 42),
                arguments(new TypeLiteral<Integer>() {}, qualifier, 42),
                arguments(new TypeLiteral<List<Integer>>() {}, none, List.of(4711)),
                arguments(new TypeLiteral<List<Integer>>() {}, qualifier, List.of(4711))

                //@formatter:on
        );
    }

    @BeforeEach
    void setUp() {
        observer.getEvents().clear();
    }

    @ParameterizedTest
    @MethodSource("getArguments")
    <T> void testSelectUnchecked(final TypeLiteral<T> beanType, Annotation[] qualifiers, T payload)
            throws InterruptedException {
        final Type payloadType = beanType.getType();
        Event<T> event = enhancedEvent.selectUnchecked(payloadType, qualifiers);

        event.fire(payload);

        final EventContext<Object> eventContext = observer.getEvents().take();

        assertThat(eventContext.getEvent()).isSameAs(payload);
        if (qualifiers.length > 0) {
            assertThat(eventContext.getMetadata().getQualifiers()).contains(qualifiers);
        }
    }

    @Test
    void testResolutionOfNormalEvent() {
        final Event<Integer> normalEvent = weld.select(new TypeLiteral<Event<Integer>>() {}).get();
        assertThat(normalEvent).isNotInstanceOf(EnhancedEvent.class);
    }

    @Singleton
    private static class Observer {
        private final BlockingQueue<EventContext<Object>> events = new LinkedBlockingQueue<>();

        private void onEvent(@Observes Object payload, EventMetadata eventMetadata) {
            events.add(new EventContextImpl<>(payload, eventMetadata));
        }

        public BlockingQueue<EventContext<Object>> getEvents() {
            return events;
        }
    }

    private static class EventContextImpl<T> implements EventContext<T> {

        private final T event;
        private final EventMetadata metadata;

        private EventContextImpl(T event, EventMetadata metadata) {
            this.event = event;
            this.metadata = metadata;
        }

        @Override
        public T getEvent() {
            return this.event;
        }

        @Override
        public EventMetadata getMetadata() {
            return this.metadata;
        }
    }
}