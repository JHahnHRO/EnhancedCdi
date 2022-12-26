package io.github.jhahnhro.enhancedcdi.messaging;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import javax.enterprise.util.AnnotationLiteral;

@Retention(RetentionPolicy.RUNTIME)
public @interface WithRoutingKey {
    String value();

    class Literal extends AnnotationLiteral<WithRoutingKey> implements WithRoutingKey {

        private final String routingKey;

        public Literal(String routingKey) {this.routingKey = routingKey;}

        @Override
        public String value() {
            return routingKey;
        }
    }
}
