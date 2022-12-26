package io.github.jhahnhro.enhancedcdi.messaging;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Qualifier;

@Qualifier
@Retention(RetentionPolicy.RUNTIME)
public @interface FromExchange {
    String value();

    class Literal extends AnnotationLiteral<FromExchange> implements FromExchange {
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
