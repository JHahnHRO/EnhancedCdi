package io.github.jhahnhro.enhancedcdi.messaging;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.enterprise.util.Nonbinding;
import jakarta.inject.Qualifier;

@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface Header {
    @Nonbinding String value();

    @SuppressWarnings("java:S2160") // Sonar wants us to override equals(), but AnnotationLiteral does not need that
    final class Literal extends AnnotationLiteral<Header> implements Header {
        private final String value;

        public Literal(String value) {this.value = value;}

        @Override
        public String value() {
            return value;
        }
    }
}
