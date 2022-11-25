package io.github.jhahnhro.enhancedcdi.context;

import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Qualifier;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface BeforePaused {
    Class<? extends Annotation> value();

    class Literal extends AnnotationLiteral<BeforePaused> implements BeforePaused {
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
