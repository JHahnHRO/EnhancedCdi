package io.github.jhahnhro.enhancedcdi.context;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Qualifier;

@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface Paused {
    Class<? extends Annotation> value();

    class Literal extends AnnotationLiteral<Paused> implements Paused {
        private final Class<? extends Annotation> scope;

        public Literal(Class<? extends Annotation> scope) {this.scope = scope;}

        public static Literal of(Class<? extends Annotation> scope) {
            return new Literal(scope);
        }

        @Override
        public Class<? extends Annotation> value() {
            return scope;
        }
    }
}
