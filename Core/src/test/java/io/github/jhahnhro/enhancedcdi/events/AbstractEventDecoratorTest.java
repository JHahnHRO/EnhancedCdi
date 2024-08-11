package io.github.jhahnhro.enhancedcdi.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.github.jhahnhro.enhancedcdi.metadata.InjectionPointImpl;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbstractEventDecoratorTest {

    private static final Annotation ADDITIONAL_QUALIFIER = new AnnotationLiteral<TestQualifier2>() {};
    private static final Annotation QUALIFIER = new AnnotationLiteral<TestQualifier>() {};

    @Mock
    private Event<Object> eventDelegate;

    private AbstractEventDecorator<Object> decoratorUnderTest;

    private void setUpWith(Type type, Annotation... qualifiers) {
        InjectionPoint injectionPoint = new InjectionPointImpl(type, qualifiers);
        EventMetadata eventMetadata = new EventMetadataImpl(injectionPoint, type, Set.of(qualifiers));

        this.decoratorUnderTest = new TestDecorator<>(eventDelegate, eventMetadata);
    }

    private static class TestDecorator<T> extends AbstractEventDecorator<T> {

        protected TestDecorator(final Event<T> delegate, final EventMetadata eventMetadata) {
            super(delegate, eventMetadata);
        }

        @Override
        protected <U extends T> AbstractEventDecorator<U> decorate(final Event<U> delegate,
                                                                   final EventMetadata eventMetadata) {
            return new TestDecorator<>(delegate, eventMetadata);
        }
    }

    @DisplayName("The select methods should all call the decorate-Method")
    @Nested
    class Decoration {

        @Mock
        Event<?> resultEvent;

        private static void verifyQualifiers(AbstractEventDecorator<?> child) {
            assertThat(child.eventMetadata.getQualifiers()).isEqualTo(Set.of(QUALIFIER, ADDITIONAL_QUALIFIER));
        }

        private static void verifyCorrectType(Event<?> child) {
            assertThat(child).isInstanceOf(TestDecorator.class);
        }

        @BeforeEach
        void setUp() {
            setUpWith(Object.class, QUALIFIER);
        }

        @Test
        void whenSelect_thenCallDecorateAndReturnItsResult() {
            when(eventDelegate.select(ADDITIONAL_QUALIFIER)).thenReturn((Event<Object>) resultEvent);
            final Event<Object> childEvent = decoratorUnderTest.select(ADDITIONAL_QUALIFIER);

            verifyCorrectType(childEvent);
            verifyQualifiers((AbstractEventDecorator<?>) childEvent);
        }

        @Test
        void whenSelectWithClass_thenCallDecorateAndReturnItsResult() {
            when(eventDelegate.select(String.class, ADDITIONAL_QUALIFIER)).thenReturn((Event<String>) resultEvent);
            final Event<String> childEvent = decoratorUnderTest.select(String.class, ADDITIONAL_QUALIFIER);

            verifyCorrectType(childEvent);
            verifyQualifiers((AbstractEventDecorator<?>) childEvent);
        }

        @Test
        void whenSelectWithTypeLiteral_thenCallDecorateAndReturnItsResult() {
            final TypeLiteral<String> typeLiteral = new TypeLiteral<>() {};
            when(eventDelegate.select(typeLiteral, ADDITIONAL_QUALIFIER)).thenReturn((Event<String>) resultEvent);
            final Event<String> childEvent = decoratorUnderTest.select(typeLiteral, ADDITIONAL_QUALIFIER);

            verifyCorrectType(childEvent);
            verifyQualifiers((AbstractEventDecorator<?>) childEvent);
        }
    }

    @DisplayName("All methods of the Event interface should ultimately call the delegate")
    @Nested
    class Delegation {

        static final String PAYLOAD = "payload";

        @BeforeEach
        void setUp() {
            setUpWith(Object.class);
        }

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
            NotificationOptions options = NotificationOptions.builder()
                    .set("myOption", 42)
                    .setExecutor(Runnable::run)
                    .build();
            final CompletionStage<String> completionStage = decoratorUnderTest.fireAsync(PAYLOAD, options);

            verify(eventDelegate).fireAsync(PAYLOAD, options);
            assertThat(completionStage).isNotNull();
        }

        @Test
        void whenSelect_thenDelegateAndReturnSomething() {
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
            final TypeLiteral<String> typeLiteral = new TypeLiteral<>() {};
            final Event<String> childEvent = decoratorUnderTest.select(typeLiteral, ADDITIONAL_QUALIFIER);

            verify(eventDelegate).select(typeLiteral, ADDITIONAL_QUALIFIER);
            assertThat(childEvent).isNotNull();
        }
    }
}