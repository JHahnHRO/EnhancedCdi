package io.github.jhahn.enhancedcdi.messaging;

import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Qualifier;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Qualifier
@Retention(RetentionPolicy.RUNTIME)
public @interface Raw {
    class Literal extends AnnotationLiteral<Raw> implements Raw {
        public static final Literal INSTANCE = new Literal();
    }
}
