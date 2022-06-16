package io.github.jhahnhro.enhancedcdi.multiton.testData.trueBeans;

import io.github.jhahnhro.enhancedcdi.multiton.testData.Shape;

import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Qualifier;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

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
