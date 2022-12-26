package io.github.jhahnhro.enhancedcdi.messaging;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Qualifier;

@Qualifier
@Retention(RetentionPolicy.RUNTIME)
public @interface Redelivered {
    boolean value();

    class Literal extends AnnotationLiteral<Redelivered> implements Redelivered {
        public final static Literal YES = new Literal(true);
        public final static Literal NO = new Literal(false);

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
