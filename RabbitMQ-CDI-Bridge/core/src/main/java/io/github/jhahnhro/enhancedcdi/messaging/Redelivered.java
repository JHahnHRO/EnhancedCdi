package io.github.jhahnhro.enhancedcdi.messaging;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Qualifier;

@Qualifier
@Retention(RetentionPolicy.RUNTIME)
public @interface Redelivered {
    boolean value();

    @SuppressWarnings("java:S2160") // Sonar wants us to override equals(), but AnnotationLiteral does not need that
    final class Literal extends AnnotationLiteral<Redelivered> implements Redelivered {
        public static final Literal YES = new Literal(true);
        public static final Literal NO = new Literal(false);

        public static Literal of(boolean value) {
            return value ? YES : NO;
        }

        private final boolean value;

        private Literal(boolean value) {this.value = value;}

        @Override
        public boolean value() {
            return value;
        }
    }
}
