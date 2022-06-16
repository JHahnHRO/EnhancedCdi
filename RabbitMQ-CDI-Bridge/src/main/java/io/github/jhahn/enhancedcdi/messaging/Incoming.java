package io.github.jhahn.enhancedcdi.messaging;

import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Qualifier;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Qualifier
@Retention(RetentionPolicy.RUNTIME)
public @interface Incoming {

    class Literal extends AnnotationLiteral<Incoming> implements Incoming {
        public static Literal INSTANCE = new Literal();

        private Literal() {}
    }
}
