package io.github.jhahnhro.enhancedcdi.metadata;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Executable;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

class AnnotatedCallable<X> extends AnnotatedMember<Executable, X>
        implements javax.enterprise.inject.spi.AnnotatedCallable<X> {

    protected final List<AnnotatedParameter<X>> parameters;

    protected AnnotatedCallable(Executable callable, Map<AnnotatedElement, Set<Annotation>> annotations,
                                Type baseType, Set<Type> typeClosure, AnnotatedType<X> declaringType) {
        super(callable, annotations.get(callable), baseType, typeClosure, declaringType);
        parameters = Arrays.stream(callable.getParameters())
                .map(p -> new AnnotatedParameter<>(p, annotations.get(p), this))
                .toList();
    }

    @Override
    public List<javax.enterprise.inject.spi.AnnotatedParameter<X>> getParameters() {
        return (List) parameters;
    }
}
