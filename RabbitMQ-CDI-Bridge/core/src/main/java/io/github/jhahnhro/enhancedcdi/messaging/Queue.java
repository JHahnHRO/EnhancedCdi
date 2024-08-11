package io.github.jhahnhro.enhancedcdi.messaging;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Qualifier;

@Qualifier
@Retention(RetentionPolicy.RUNTIME)
public @interface Queue {

    @SuppressWarnings("java:S2160") // Sonar wants us to override equals(), but AnnotationLiteral does not need that
    final class Literal extends AnnotationLiteral<Queue> implements Queue {
        public static final Literal INSTANCE = new Literal();

        private Literal() {}
    }
}
