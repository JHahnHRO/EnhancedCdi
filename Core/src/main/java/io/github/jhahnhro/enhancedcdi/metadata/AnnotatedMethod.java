package io.github.jhahnhro.enhancedcdi.metadata;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

public class AnnotatedMethod<X> extends AnnotatedCallable<X> implements javax.enterprise.inject.spi.AnnotatedMethod<X> {
    protected AnnotatedMethod(Method method, Map<AnnotatedElement, Set<Annotation>> annotations,
                              AnnotatedType<X> declaringType) {
        super(method, annotations, declaringType.typeResolver.resolve(method.getGenericReturnType()),
              declaringType.typeResolver.resolvedTypeClosure(method.getGenericReturnType()), declaringType);
    }

    @Override
    public Method getJavaMember() {
        return (Method) annotatedElement;
    }
}
