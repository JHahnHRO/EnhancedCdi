package io.github.jhahnhro.enhancedcdi.messaging;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Qualifier;

/**
 * A bean {@link jakarta.enterprise.context.Dependent}-scoped of type {@link String} with this qualifier is available
 * whose value is the exchange the current message was published to.
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
public @interface Exchange {

    final class Literal extends AnnotationLiteral<Exchange> implements Exchange {
        public static final Literal INSTANCE = new Literal();

        private Literal() {}
    }
}
