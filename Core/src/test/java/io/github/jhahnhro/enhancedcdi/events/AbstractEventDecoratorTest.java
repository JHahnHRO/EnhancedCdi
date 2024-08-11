package io.github.jhahnhro.enhancedcdi.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.lang.annotation.Annotation;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.enterprise.inject.spi.EventMetadata;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.enterprise.util.TypeLiteral;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbstractEventDecoratorTest {

    private static final Annotation ADDITIONAL_QUALIFIER = new AnnotationLiteral<TestQualifier2>() {
    };
    private static final Annotation QUALIFIER = new AnnotationLiteral<TestQualifier>() {
    };

    @Mock
    private Event<Object> eventDelegate;

    @Mock(lenient = true)
    private EventMetadata metadataMock;

    @Mock
    private InjectionPoint injectionPointMock;

    private AbstractEventDecorator<Object> decoratorUnderTest;

    @BeforeEach
    void setUp() {
        decoratorUnderTest = new MinimalDecoratorImpl<>(eventDelegate, metadataMock);
        when(metadataMock.getInjectionPoint()).thenReturn(injectionPointMock);
    }

    private static class MinimalDecoratorImpl<T> extends AbstractEventDecorator<T> {

        protected MinimalDecoratorImpl(final Event<T> delegate, final EventMetadata eventMetadata) {
            super(delegate, eventMetadata);
        }

        @Override
        protected <U extends T> AbstractEventDecorator<U> decorate(final Event<U> delegate,
                                                                   final EventMetadata eventMetadata) {
            return new MinimalDecoratorImpl<>(delegate, eventMetadata);
        }
    }

    @DisplayName("The select methods should all call the decorate-Method")
    @Nested
    class Decoration {

        AbstractEventDecorator<Object> decoratorSpy;

        @Mock
        AbstractEventDecorator<Object> resultDecorator;

        @Mock
        Event<?> resultEvent;

        @BeforeEach
        void setUp() {
            decoratorSpy = Mockito.spy(decoratorUnderTest);
            when(decoratorSpy.decorate(any(), any())).thenReturn(resultDecorator);
            when(metadataMock.getQualifiers()).thenReturn(Set.of(QUALIFIER));
        }

        @Test
        void whenSelect_thenCallDecorateAndReturnItsResult() {
            when(metadataMock.getType()).thenReturn(Object.class);
            when(eventDelegate.select(ADDITIONAL_QUALIFIER)).thenReturn((Event<Object>) resultEvent);
            final Event<Object> childEvent = decoratorSpy.select(ADDITIONAL_QUALIFIER);

            verifyAdditionalQualifier();
            assertThat(childEvent).isSameAs(resultDecorator);
        }

        void verifyAdditionalQualifier() {
            ArgumentCaptor<EventMetadata> metadataCaptor = ArgumentCaptor.forClass(EventMetadata.class);
            verify(decoratorSpy).decorate(eq(resultEvent), metadataCaptor.capture());
            assertThat(metadataCaptor.getValue().getQualifiers()).isEqualTo(Set.of(QUALIFIER, ADDITIONAL_QUALIFIER));
        }

        @Test
        void whenSelectWithClass_thenCallDecorateAndReturnItsResult() {
            when(eventDelegate.select(String.class, ADDITIONAL_QUALIFIER)).thenReturn((Event<String>) resultEvent);
            final Event<String> childEvent = decoratorSpy.select(String.class, ADDITIONAL_QUALIFIER);

            verifyAdditionalQualifier();
            assertThat(childEvent).isSameAs(resultDecorator);
        }

        @Test
        void whenSelectWithTypeLiteral_thenCallDecorateAndReturnItsResult() {
            final TypeLiteral<String> typeLiteral = new TypeLiteral<>() {
            };
            when(eventDelegate.select(typeLiteral, ADDITIONAL_QUALIFIER)).thenReturn((Event<String>) resultEvent);
            final Event<String> childEvent = decoratorSpy.select(typeLiteral, ADDITIONAL_QUALIFIER);

            verifyAdditionalQualifier();
            assertThat(childEvent).isSameAs(resultDecorator);
        }
    }

    @DisplayName("All methods of the Event interface should ultimately call the delegate")
    @Nested
    class Delegation {

        static final String PAYLOAD = "payload";

        @Test
        void whenFire_thenDelegate() {
            decoratorUnderTest.fire(PAYLOAD);

            verify(eventDelegate).fire(PAYLOAD);
        }

        @Test
        void whenFireAsync_thenDelegateAndReturnSomething() {
            when(eventDelegate.fireAsync(any())).thenReturn(CompletableFuture.completedFuture(null));
            final CompletionStage<String> completionStage = decoratorUnderTest.fireAsync(PAYLOAD);

            verify(eventDelegate).fireAsync(PAYLOAD);
            assertThat(completionStage).isNotNull();
        }

        @Test
        void whenFireAsyncWithNotificationOptions_thenDelegateAndReturnSomething() {
            when(eventDelegate.fireAsync(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
            NotificationOptions options =
                    NotificationOptions.builder().set("myOption", 42).setExecutor(mock(Executor.class)).build();
            final CompletionStage<String> completionStage = decoratorUnderTest.fireAsync(PAYLOAD, options);

            verify(eventDelegate).fireAsync(PAYLOAD, options);
            assertThat(completionStage).isNotNull();
        }

        @Test
        void whenSelect_thenDelegateAndReturnSomething() {
            when(metadataMock.getType()).thenReturn(Object.class);
            final Event<Object> childEvent = decoratorUnderTest.select(ADDITIONAL_QUALIFIER);

            verify(eventDelegate).select(ADDITIONAL_QUALIFIER);
            assertThat(childEvent).isNotNull();
        }

        @Test
        void whenSelectWithClass_thenDelegateAndReturnSomething() {
            final Event<String> childEvent = decoratorUnderTest.select(String.class, ADDITIONAL_QUALIFIER);

            verify(eventDelegate).select(String.class, ADDITIONAL_QUALIFIER);
            assertThat(childEvent).isNotNull();
        }

        @Test
        void whenSelectWithTypeLiteral_thenDelegateAndReturnSomething() {
            final TypeLiteral<String> typeLiteral = new TypeLiteral<>() {
            };
            final Event<String> childEvent = decoratorUnderTest.select(typeLiteral, ADDITIONAL_QUALIFIER);

            verify(eventDelegate).select(typeLiteral, ADDITIONAL_QUALIFIER);
            assertThat(childEvent).isNotNull();
        }
    }
}