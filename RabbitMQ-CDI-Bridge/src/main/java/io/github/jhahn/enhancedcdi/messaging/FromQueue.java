package io.github.jhahn.enhancedcdi.messaging;

import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Qualifier;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Qualifier
@Retention(RetentionPolicy.RUNTIME)
public @interface FromQueue {
    String value();

    class Literal extends AnnotationLiteral<FromQueue> implements FromQueue {
        private final String name;

        public Literal(String name) {this.name = name;}

        @Override
        public String value() {
            return name;
        }
    }
}
