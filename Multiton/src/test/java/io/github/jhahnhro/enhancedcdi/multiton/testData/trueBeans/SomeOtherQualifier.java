package io.github.jhahnhro.enhancedcdi.multiton.testData.trueBeans;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.github.jhahnhro.enhancedcdi.multiton.testData.Shape;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Qualifier;

@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface SomeOtherQualifier {
    Shape value();

    public class Literal extends AnnotationLiteral<SomeOtherQualifier> implements SomeOtherQualifier {
        private final Shape shape;

        public Literal(Shape shape) {this.shape = shape;}

        @Override
        public Shape value() {
            return shape;
        }
    }
}
