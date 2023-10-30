package io.github.jhahnhro.enhancedcdi.multiton.testData.validation;

import javax.enterprise.util.AnnotationLiteral;

import io.github.jhahnhro.enhancedcdi.multiton.testData.Color;

@SuppressWarnings("unused")
public class AnnotationsWithInvalidFactories {
    public @interface TwoFactories {
        class Literal extends AnnotationLiteral<TwoFactories> implements TwoFactories {
            public static Literal factory1(Color color) {
                return null;
            }

            public static Literal factory2(Color color) {
                return null;
            }
        }
    }
}
