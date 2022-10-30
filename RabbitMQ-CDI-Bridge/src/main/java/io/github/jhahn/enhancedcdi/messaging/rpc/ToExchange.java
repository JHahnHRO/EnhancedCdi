package io.github.jhahn.enhancedcdi.messaging.rpc;

import javax.enterprise.util.AnnotationLiteral;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface ToExchange {
    String value();

    class Literal extends AnnotationLiteral<ToExchange> implements ToExchange {
        private final String name;

        public Literal(String name) {
            this.name = name;
        }

        @Override
        public String value() {
            return name;
        }
    }
}
