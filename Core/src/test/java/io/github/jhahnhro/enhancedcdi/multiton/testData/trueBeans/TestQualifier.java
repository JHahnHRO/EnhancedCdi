package io.github.jhahnhro.enhancedcdi.multiton.testData.trueBeans;

import io.github.jhahnhro.enhancedcdi.multiton.testData.Color;

import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Qualifier;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

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
