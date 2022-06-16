package io.github.jhahnhro.enhancedcdi.metadata;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.Set;

public class AnnotatedConstructor<X> extends AnnotatedCallable<X>
        implements javax.enterprise.inject.spi.AnnotatedConstructor<X> {
    protected AnnotatedConstructor(Constructor<X> constructor, Map<AnnotatedElement, Set<Annotation>> annotations,
                                   AnnotatedType<X> declaringType) {
        super(constructor, annotations, declaringType.getBaseType(), declaringType.getTypeClosure(), declaringType);
    }

    @Override
    public Constructor<X> getJavaMember() {
        return (Constructor<X>) annotatedElement;
    }
}
