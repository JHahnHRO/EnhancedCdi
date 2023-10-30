package io.github.jhahnhro.enhancedcdi.multiton.testData.validation;

import javax.enterprise.util.AnnotationLiteral;

import io.github.jhahnhro.enhancedcdi.multiton.testData.Color;

@SuppressWarnings("unused") // used as test inputs for reflective access
public class AnnotationsWithoutFactories {

    public @interface OnlyDefaultConstructor {
        class Literal extends AnnotationLiteral<OnlyDefaultConstructor> implements OnlyDefaultConstructor {}
    }

    public @interface PrivateConstructor {
        class Literal extends AnnotationLiteral<PrivateConstructor> implements PrivateConstructor {
            private Literal () {
            }
        }
    }

    public @interface PrivateStaticFactory {
        class Literal extends AnnotationLiteral<PrivateStaticFactory> implements PrivateStaticFactory {
            private static Literal factory (Color color) {
                return null;
            }
        }
    }

    public @interface PrivateStaticOfMethod {
        class Literal extends AnnotationLiteral<PrivateStaticOfMethod> implements PrivateStaticOfMethod {
            private static Literal of (Color color) {
                return null;
            }
        }
    }

    public @interface PublicNonstaticFactory {
        class Literal extends AnnotationLiteral<PublicNonstaticFactory> implements PublicNonstaticFactory {
            public Literal factory (Color color) {
                return null;
            }
        }
    }

    public @interface PublicNonstaticOfMethod {
        class Literal extends AnnotationLiteral<PublicNonstaticOfMethod> implements PublicNonstaticOfMethod {
            public Literal of (Color color) {
                return null;
            }
        }
    }


    public @interface OfMethodWrongReturnType {
        class Literal extends AnnotationLiteral<OfMethodWrongReturnType> implements OfMethodWrongReturnType {
            public static Object of (Color color) {
                return null;
            }
        }
    }

}
