package io.github.jhahnhro.enhancedcdi.metadata;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @param <T>
 */
public class AnnotatedTypeConfigurator<T>
        extends AnnotatedElementConfigurator<jakarta.enterprise.inject.spi.AnnotatedType<T>, AnnotatedTypeConfigurator<T>>
        implements jakarta.enterprise.inject.spi.configurator.AnnotatedTypeConfigurator<T> {

    private final Set<AnnotatedConstructorConfigurator<T>> constructors;
    private final Set<AnnotatedMethodConfigurator<T>> allMethods;
    private final Set<AnnotatedFieldConfigurator<T>> allFields;

    public AnnotatedTypeConfigurator(jakarta.enterprise.inject.spi.AnnotatedType<T> originalType) {
        super(originalType);

        this.constructors = originalType.getConstructors()
                .stream()
                .map(ctor -> new AnnotatedConstructorConfigurator<>(ctor, this))
                .collect(Collectors.toUnmodifiableSet());

        this.allFields = originalType.getFields()
                .stream()
                .map(field -> new AnnotatedFieldConfigurator<>((jakarta.enterprise.inject.spi.AnnotatedField<T>) field,
                                                               this))
                .collect(Collectors.toUnmodifiableSet());

        this.allMethods = originalType.getMethods()
                .stream()
                .map(method -> new AnnotatedMethodConfigurator<>(
                        (jakarta.enterprise.inject.spi.AnnotatedMethod<T>) method, this))
                .collect(Collectors.toUnmodifiableSet());

    }

    @Override
    public Set<jakarta.enterprise.inject.spi.configurator.AnnotatedMethodConfigurator<? super T>> methods() {
        return (Set) allMethods;
    }

    public Set<AnnotatedMethodConfigurator<T>> getMethods() {
        return allMethods;
    }

    @Override
    public Set<jakarta.enterprise.inject.spi.configurator.AnnotatedFieldConfigurator<? super T>> fields() {
        return (Set) allFields;
    }

    public Set<AnnotatedFieldConfigurator<T>> getFields() {
        return allFields;
    }

    @Override
    public Set<jakarta.enterprise.inject.spi.configurator.AnnotatedConstructorConfigurator<T>> constructors() {
        return (Set) constructors;
    }

    public Set<AnnotatedConstructorConfigurator<T>> getConstructors() {
        return constructors;
    }

    @Override
    public AnnotatedType<T> build() {
        final Class<T> myClass = getAnnotated().getJavaClass();
        Map<AnnotatedElement, Set<Annotation>> allAnnotations = new HashMap<>();

        allAnnotations.put(myClass, this.getAnnotations());

        for (AnnotatedConstructorConfigurator<T> c : constructors) {
            allAnnotations.put(c.getAnnotated().getJavaMember(), c.getAnnotations());
            c.getParameters().forEach(p -> allAnnotations.put(p.getAnnotated().getJavaParameter(), p.getAnnotations()));
        }
        for (AnnotatedMethodConfigurator<T> m : allMethods) {
            allAnnotations.put(m.getAnnotated().getJavaMember(), m.getAnnotations());
            m.getParameters().forEach(p -> allAnnotations.put(p.getAnnotated().getJavaParameter(), p.getAnnotations()));
        }
        for (AnnotatedFieldConfigurator<T> f : allFields) {
            allAnnotations.put(f.getAnnotated().getJavaMember(), f.getAnnotations());
        }

        return new AnnotatedType<>(myClass, allAnnotations, getAnnotated().getBaseType(),
                                   getAnnotated().getTypeClosure());
    }

    @Override
    public AnnotatedTypeConfigurator<T> reset() {
        constructors.forEach(AnnotatedElementConfigurator::reset);
        allMethods.forEach(AnnotatedElementConfigurator::reset);
        allFields.forEach(AnnotatedElementConfigurator::reset);
        return super.reset();
    }
}
