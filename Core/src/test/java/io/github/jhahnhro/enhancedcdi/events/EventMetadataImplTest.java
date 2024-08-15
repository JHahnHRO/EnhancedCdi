package io.github.jhahnhro.enhancedcdi.events;

import static org.assertj.core.api.Assertions.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.List;
import java.util.stream.Stream;

import io.github.jhahnhro.enhancedcdi.metadata.InjectionPointImpl;
import io.github.jhahnhro.enhancedcdi.util.EnhancedInstance;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.EventMetadata;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Provider;
import org.jboss.weld.events.WeldEvent;
import org.jboss.weld.inject.WeldInstance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class EventMetadataImplTest {

    public static Stream<Type> nonEventTypes() {
        return Stream.of(
                //@formatter:off
                new TypeLiteral<Integer>(){}.getType(),
                new TypeLiteral<List<Integer>>(){}.getType(),
                new TypeLiteral<Instance<Integer>>(){}.getType(),
                new TypeLiteral<Instance<List<Integer>>>(){}.getType()
                //@formatter:on
        );
    }

    public static Stream<Type> eventTypes() {
        return Stream.of(
                //@formatter:off
                new TypeLiteral<Event<Integer>>(){}.getType(),
                new TypeLiteral<WeldEvent<Integer>>(){}.getType(),
                new TypeLiteral<EnhancedEvent<Integer>>(){}.getType(),
                // the other types are weird, but technically allowed
                new TypeLiteral<Provider<Event<Integer>>>(){}.getType(),
                new TypeLiteral<Instance<Event<Integer>>>(){}.getType(),
                new TypeLiteral<WeldInstance<Event<Integer>>>(){}.getType(),
                new TypeLiteral<EnhancedInstance<Event<Integer>>>(){}.getType(),
                new TypeLiteral<Provider<Instance<Event<Integer>>>>(){}.getType(),
                new TypeLiteral<Instance<Provider<Event<Integer>>>>(){}.getType()
                //@formatter:on
        );
    }

    @Test
    @DisplayName("Constructor should extract necessary information directly from an Event<T> injection point")
    void givenInjectionPointOfEventType_whenConstructor_thenSucceed() {
        final TypeLiteral<Event<Integer>> typeLiteral = new TypeLiteral<>() {};
        final Annotation qualifier = new AnnotationLiteral<>() {};

        var injectionPoint = new InjectionPointImpl(typeLiteral.getType(), qualifier);

        final EventMetadata eventMetadata = new EventMetadataImpl(injectionPoint);

        assertThat(eventMetadata.getInjectionPoint()).isSameAs(injectionPoint);
        assertThat(eventMetadata.getQualifiers()).containsExactly(qualifier);
        assertThat(eventMetadata.getType()).isEqualTo(Integer.class);
    }


    @ParameterizedTest
    @MethodSource("eventTypes")
    @DisplayName("Constructor should handle all event types, even weird ones like Provider<Event<T>>")
    void givenEventTypes_whenConstructor_thenSucceed(Type type) {
        var injectionPoint = new InjectionPointImpl(type);

        final EventMetadata eventMetadata = new EventMetadataImpl(injectionPoint);

        assertThat(eventMetadata.getType()).isEqualTo(Integer.class);
    }

    @ParameterizedTest
    @MethodSource("nonEventTypes")
    @DisplayName("Constructor should throw an IllegalArgumentException if the injection point does not have event type")
    void givenInjectionPointOfNonEventType_whenConstructor_thenThrowIAE(Type type) {
        var injectionPoint = new InjectionPointImpl(type);
        assertThatThrownBy(() -> new EventMetadataImpl(injectionPoint)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenRecursiveParametrizedType_whenCtor_thenThrowIAE() {
        // recursive parametrized types !
        abstract class Foobar<T> implements Provider<Foobar<T>> {} //

        var injectionPoint = new InjectionPointImpl(new TypeLiteral<Foobar<?>>() {}.getType());

        assertThatIllegalArgumentException().isThrownBy(() -> new EventMetadataImpl(injectionPoint));
    }
}