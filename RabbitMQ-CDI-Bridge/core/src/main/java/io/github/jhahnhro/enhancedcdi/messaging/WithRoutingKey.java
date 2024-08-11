package io.github.jhahnhro.enhancedcdi.messaging;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Qualifier;

@Qualifier
@Retention(RetentionPolicy.RUNTIME)
public @interface WithRoutingKey {
    String value();

    @SuppressWarnings("java:S2160") // Sonar wants us to override equals(), but AnnotationLiteral does not need that
    final class Literal extends AnnotationLiteral<WithRoutingKey> implements WithRoutingKey {

        private final String routingKey;

        public Literal(String routingKey) {this.routingKey = routingKey;}

        @Override
        public String value() {
            return routingKey;
        }
    }
}
