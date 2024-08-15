package io.github.jhahnhro.enhancedcdi.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Stream;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.EventContext;
import jakarta.enterprise.inject.spi.EventMetadata;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@ExtendWith(WeldJunit5Extension.class)
class EnhancedEventTest {

    @WeldSetup
    WeldInitiator weld = WeldInitiator.from(EnhancedEvent.class, Observer.class, TestQualifier.class).build();
    @Inject
    EnhancedEvent<Object> enhancedEvent;

    @Inject
    Observer observer;

    @BeforeEach
    void setUp() {
        // ignore container lifecycle events
        observer.getEvents().clear();
    }

    @Test
    void cdiContainerShouldStillCorrectlyResolveBuiltInEvent() {
        // throws AmbigousResolutionException if there were more than two matching beans
        final Event<Integer> normalEvent = weld.select(new TypeLiteral<Event<Integer>>() {}).get();
        assertThat(normalEvent).isNotInstanceOf(EnhancedEvent.class);
    }

    @Nested
    class TestSelectUnchecked {

        static Stream<Arguments> payloadAndQualifiers() {
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

        static Stream<Arguments> illegalSubtypes() {
            return Stream.of(
                    //@formatter:off
                    arguments(new TypeLiteral<String>() {}, new TypeLiteral<Integer>() {}),
                    arguments(new TypeLiteral<Iterable<String>>() {}, new TypeLiteral<List<Integer>>() {})
                    //@formatter:on
            );
        }

        static Stream<Arguments> legalSubtypes() {
            return Stream.of(
                    //@formatter:off
                    arguments(new TypeLiteral<CharSequence>() {}, new TypeLiteral<String>() {}),
                    arguments(new TypeLiteral<Iterable<String>>() {}, new TypeLiteral<List<String>>() {})
                    //@formatter:on
            );
        }

        @ParameterizedTest
        @MethodSource("payloadAndQualifiers")
        <T> void givenQualifiers_whenSelectUncheckedFire_thenEventUsesCorrectQualifiers(final TypeLiteral<T> beanType
                , Annotation[] qualifiers, T payload)
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

        @ParameterizedTest
        @MethodSource("illegalSubtypes")
        <T> void givenIllegalSubtype_whenSelectUnchecked_thenThrowIAE(TypeLiteral<T> eventType,
                                                                      TypeLiteral<?> illegalSubtype) {
            // selectUnchecked from Object to eventType should always succeed
            final Type typeT = eventType.getType();
            EnhancedEvent<T> childEvent = enhancedEvent.selectUnchecked(typeT);
            assertThat(childEvent.eventMetadata.getType()).isEqualTo(typeT);

            assertThatIllegalArgumentException().isThrownBy(() -> childEvent.selectUnchecked(illegalSubtype.getType()));
        }

        @ParameterizedTest
        @MethodSource("legalSubtypes")
        <T, U extends T> void givenLegalSubtype_whenSelectUnchecked_thenDoNoThrow(TypeLiteral<T> eventType,
                                                                                  TypeLiteral<U> subtype) {
            // selectUnchecked from Object to eventType should always succeed
            final Type typeT = eventType.getType();
            EnhancedEvent<T> childEvent = enhancedEvent.selectUnchecked(typeT);
            assertThat(childEvent.eventMetadata.getType()).isEqualTo(typeT);

            final Type typeU = subtype.getType();
            EnhancedEvent<U> childEvent2 = childEvent.selectUnchecked(typeU);
            assertThat(childEvent2.eventMetadata.getType()).isEqualTo(typeU);
        }
    }

    @Nested
    class TestInjectionPoint {

        @Test
        void whenFire_thenOriginalInjectionPointIsUsed() throws InterruptedException {
            enhancedEvent.fire("foobar");

            final EventContext<Object> eventContext = observer.getEvents().take();
            final InjectionPoint injectionPoint = eventContext.getMetadata().getInjectionPoint();

            // verify that it is the injection point in this class, not an auxiliary injection point in EnhancedEvent
            assertThat(injectionPoint.getMember().getDeclaringClass()).isEqualTo(EnhancedEventTest.class);
        }

        @Test
        void whenSelectThenFire_thenOriginalInjectionPointIsUsed() throws InterruptedException {
            enhancedEvent.select(String.class).fire("foobar");

            final EventContext<Object> eventContext = observer.getEvents().take();
            final InjectionPoint injectionPoint = eventContext.getMetadata().getInjectionPoint();

            // verify that it is the injection point in this class, not an auxiliary injection point in EnhancedEvent
            assertThat(injectionPoint.getMember().getDeclaringClass()).isEqualTo(EnhancedEventTest.class);
        }

        @Test
        void whenSelectUncheckedThenFire_thenOriginalInjectionPointIsUsed() throws InterruptedException {
            enhancedEvent.<String>selectUnchecked(new TypeLiteral<String>() {}.getType()).fire("foobar");

            final EventContext<Object> eventContext = observer.getEvents().take();
            final InjectionPoint injectionPoint = eventContext.getMetadata().getInjectionPoint();

            // verify that it is the injection point in this class, not an auxiliary injection point in EnhancedEvent
            assertThat(injectionPoint.getMember().getDeclaringClass()).isEqualTo(EnhancedEventTest.class);
        }
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
}