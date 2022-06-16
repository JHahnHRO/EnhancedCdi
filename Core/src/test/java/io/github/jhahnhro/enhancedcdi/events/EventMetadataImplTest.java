package io.github.jhahnhro.enhancedcdi.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import javax.enterprise.event.Event;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.EventMetadata;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.util.AnnotationLiteral;
import javax.enterprise.util.TypeLiteral;
import javax.inject.Provider;

import io.github.jhahnhro.enhancedcdi.events.EventMetadataImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventMetadataImplTest {

    @Mock
    InjectionPoint injectionPoint;

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
                // the other types are weird, but technically allowed
                new TypeLiteral<Provider<Event<Integer>>>(){}.getType(),
                new TypeLiteral<Instance<Event<Integer>>>(){}.getType(),
                new TypeLiteral<Provider<Instance<Event<Integer>>>>(){}.getType(),
                new TypeLiteral<Instance<Provider<Event<Integer>>>>(){}.getType()
                //@formatter:on
        );
    }

    @Test
    @DisplayName("Constructor should extract necessary information directly from an Event<T> injection point")
    void givenInjectionPointOfEventType_whenConstructor_thenSucceed() {
        final TypeLiteral<Event<Integer>> typeLiteral = new TypeLiteral<>() {
        };
        when(injectionPoint.getType()).thenReturn(typeLiteral.getType());
        final Annotation qualifier = new AnnotationLiteral<>() {
        };
        when(injectionPoint.getQualifiers()).thenReturn(Set.of(qualifier));

        final EventMetadata eventMetadata = new EventMetadataImpl(injectionPoint);

        assertThat(eventMetadata.getInjectionPoint()).isSameAs(injectionPoint);
        assertThat(eventMetadata.getQualifiers()).containsExactly(qualifier);
        assertThat(eventMetadata.getType()).isEqualTo(Integer.class);
    }


    @ParameterizedTest
    @MethodSource("eventTypes")
    @DisplayName("Constructor should handle all event types, even weird ones like Provider<Event<T>>")
    void givenEventTypes_whenConstructor_thenSucceed(Type type) {
        when(injectionPoint.getType()).thenReturn(type);
        final Annotation qualifier = new AnnotationLiteral<>() {
        };
        when(injectionPoint.getQualifiers()).thenReturn(Set.of(qualifier));

        final EventMetadata eventMetadata = new EventMetadataImpl(injectionPoint);

        assertThat(eventMetadata.getType()).isEqualTo(Integer.class);
    }

    @ParameterizedTest
    @MethodSource("nonEventTypes")
    @DisplayName("Constructor should throw an IllegalArgumentException if the injection point does not have event type")
    void givenInjectionPointOfNonEventType_whenConstructor_thenThrowIAE(Type type) {
        when(injectionPoint.getType()).thenReturn(type);
        assertThatThrownBy(() -> new EventMetadataImpl(injectionPoint)).isInstanceOf(IllegalArgumentException.class);
    }
}