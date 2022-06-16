package io.github.jhahn.enhancedcdi.messaging;

import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Qualifier;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Qualifier
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
