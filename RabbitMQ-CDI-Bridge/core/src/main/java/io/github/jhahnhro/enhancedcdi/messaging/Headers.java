package io.github.jhahnhro.enhancedcdi.messaging;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Qualifier;

/**
 * A {@link javax.enterprise.context.Dependent}-scoped bean of type {@code Map<String,Object>} is available that
 * contains all headers of the incoming message.
 */
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface Headers {

    final class Literal extends AnnotationLiteral<Headers> implements Headers {
        public static final Literal INSTANCE = new Literal();

        private Literal() {}
    }
}
