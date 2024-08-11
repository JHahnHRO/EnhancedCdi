package io.github.jhahnhro.enhancedcdi.messaging;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Qualifier;

/**
 * This qualifier is added to any event for incoming RabbitMQ messages in case the type alone is not sufficient to
 * distinguish these events.
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
public @interface Incoming {

    final class Literal extends AnnotationLiteral<Incoming> implements Incoming {
        public static final Literal INSTANCE = new Literal();

        private Literal() {}
    }
}
