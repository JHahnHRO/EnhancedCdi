package io.github.jhahnhro.enhancedcdi.multiton.testData.trueBeans;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.github.jhahnhro.enhancedcdi.multiton.testData.Color;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Qualifier;

@Qualifier
@Retention(RetentionPolicy.RUNTIME)
public @interface TestQualifier {
    Color value ();

    class Literal extends AnnotationLiteral<TestQualifier> implements TestQualifier {
        private Color color;

        public Literal (Color color) {
            this.color = color;
        }

        @Override
        public Color value () {
            return color;
        }
    }
}
