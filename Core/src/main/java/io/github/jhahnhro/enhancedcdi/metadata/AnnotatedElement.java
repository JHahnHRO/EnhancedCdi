package io.github.jhahnhro.enhancedcdi.metadata;

import io.github.jhahnhro.enhancedcdi.reflection.TypeVariableResolver;

import javax.enterprise.inject.spi.Annotated;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

class AnnotatedElement<ELEMENT extends java.lang.reflect.AnnotatedElement> implements Annotated {

    protected final ELEMENT annotatedElement;
    protected final Set<Annotation> annotations;
    protected final Type baseType;
    protected final Set<Type> typeClosure;
    protected final TypeVariableResolver typeResolver;

    protected AnnotatedElement(ELEMENT annotatedElement, Set<Annotation> annotations, Type baseType, Set<Type> typeClosure) {
        this.annotatedElement = Objects.requireNonNull(annotatedElement);
        final Normalizer normalizer = new Normalizer();
        this.annotations = annotations.stream().flatMap(normalizer::normalize).collect(Collectors.toUnmodifiableSet());
        this.baseType = Objects.requireNonNull(baseType);
        this.typeClosure = Set.copyOf(typeClosure);
        this.typeResolver = TypeVariableResolver.withKnownTypesOf(baseType);
    }

    public ELEMENT getAnnotatedElement() {
        return annotatedElement;
    }

    @Override
    public Type getBaseType() {
        return baseType;
    }

    @Override
    public Set<Type> getTypeClosure() {
        return typeClosure;
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
        return getAnnotations(annotationType).stream().findAny().orElse(null);
    }

    @Override
    public <T extends Annotation> Set<T> getAnnotations(Class<T> annotationType) {
        return annotations.stream()
                .filter(annotationType::isInstance)
                .map(annotationType::cast)
                .collect(Collectors.toSet());
    }

    @Override
    public Set<Annotation> getAnnotations() {
        return annotations;
    }

    @Override
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
        return annotations.stream().anyMatch(ann -> ann.annotationType() == annotationType);
    }
}
