package io.github.jhahnhro.enhancedcdi.multiton.testData.validation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import javax.enterprise.util.AnnotationLiteral;

import io.github.jhahnhro.enhancedcdi.multiton.testData.Color;

public class AnnotationsWithValidFactories {

    @Retention(RetentionPolicy.RUNTIME)
    public @interface LiteralWithConstructor {
        Color color();

        class Literal extends AnnotationLiteral<LiteralWithConstructor> implements LiteralWithConstructor {
            public final Color myColor;
            public Literal (Color myColor) {
                this.myColor = myColor;
            }

            @Override
            public Color color () {
                return myColor;
            }
        }
    }


    @Retention(RetentionPolicy.RUNTIME)
    public @interface LiteralWithOfMethod {
        Color color();

        class Literal extends AnnotationLiteral<LiteralWithOfMethod> implements LiteralWithOfMethod {
            public final Color myColor;
            private Literal (Color myColor) {
                this.myColor = myColor;
            }
            public static Literal of(Color myColor){
                return new Literal(myColor);
            }

            @Override
            public Color color () {
                return myColor;
            }
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface LiteralWithFactory {
        Color color();

        class Literal extends AnnotationLiteral<LiteralWithFactory> implements LiteralWithFactory {
            public final Color myColor;
            private Literal (Color myColor) {
                this.myColor = myColor;
            }
            public static Literal factory(Color myColor){
                return new Literal(myColor);
            }

            @Override
            public Color color () {
                return myColor;
            }
        }
    }
}
