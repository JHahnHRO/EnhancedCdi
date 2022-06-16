package io.github.jhahnhro.enhancedcdi.multiton.testData.validation;

import io.github.jhahnhro.enhancedcdi.multiton.testData.Color;
import io.github.jhahnhro.enhancedcdi.multiton.testData.TestException;

import javax.enterprise.util.AnnotationLiteral;

@SuppressWarnings("unused")
public class AnnotationsWithFailingFactory {

    public @interface ReturnsNullSometimes {
        class Literal extends AnnotationLiteral<ReturnsNullSometimes> implements ReturnsNullSometimes {
            public static Literal of (Color input) {
                if (input == Color.RED) {
                    return null;
                } else {
                    return new Literal();
                }
            }
        }
    }

    public @interface ReturnsNullAlways {
        class Literal extends AnnotationLiteral<ReturnsNullAlways> implements ReturnsNullAlways {
            public static Literal of (Color input) {
                return null;
            }
        }
    }


    public @interface ThrowsSometimes {
        class Literal extends AnnotationLiteral<ThrowsSometimes> implements ThrowsSometimes {
            public static Literal of (Color input) throws TestException {
                if (input == Color.RED) {
                    throw new TestException();
                } else {
                    return new Literal();
                }
            }
        }
    }

    public @interface ThrowsAlways {
        class Literal extends AnnotationLiteral<ThrowsAlways> implements ThrowsAlways {
            public static Literal of (Color input) throws TestException {
                throw new TestException();
            }
        }
    }
}
