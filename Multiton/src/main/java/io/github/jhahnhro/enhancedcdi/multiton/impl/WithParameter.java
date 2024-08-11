package io.github.jhahnhro.enhancedcdi.multiton.impl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.enterprise.util.AnnotationLiteral;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE,ElementType.METHOD})
public @interface WithParameter {
    Class<?> parameterClass();
    String stringRepresentation();

    class Literal<P> extends AnnotationLiteral<WithParameter> implements WithParameter {
        private final Class<P> parameterClass;
        private final String stringRepresentation;

        public Literal(Class<P> parameterClass, String stringRepresentation) {
            this.parameterClass = parameterClass;
            this.stringRepresentation = stringRepresentation;
        }

        @Override
        public Class<P> parameterClass() {
            return parameterClass;
        }

        @Override
        public String stringRepresentation() {
            return stringRepresentation;
        }
    }
}
