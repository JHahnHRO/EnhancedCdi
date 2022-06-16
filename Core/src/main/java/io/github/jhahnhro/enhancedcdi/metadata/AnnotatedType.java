package io.github.jhahnhro.enhancedcdi.metadata;

import io.github.jhahnhro.enhancedcdi.reflection.TypeVariableResolver;
import io.github.jhahnhro.enhancedcdi.reflection.Visit;
import io.github.jhahnhro.enhancedcdi.reflection.Visit.ClassHierarchy.RecursiveVisitor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

public class AnnotatedType<T> extends AnnotatedElement<Class<T>>
        implements javax.enterprise.inject.spi.AnnotatedType<T> {
    final Set<AnnotatedConstructor<T>> constructors;
    final Set<AnnotatedMethod<T>> methods;
    final Set<AnnotatedField<T>> fields;

    AnnotatedType(Class<T> clazz, Map<java.lang.reflect.AnnotatedElement, Set<Annotation>> allAnnotations,
                  Type baseType, Set<Type> typeClosure) {
        super(clazz, allAnnotations.get(clazz), baseType, typeClosure);

        this.constructors = Arrays.stream(clazz.getDeclaredConstructors())
                .filter(c -> !c.isSynthetic())
                .map(ctor -> new AnnotatedConstructor<>((Constructor<T>) ctor, allAnnotations, this))
                .collect(Collectors.toUnmodifiableSet());

        Set<AnnotatedMethod<T>> methods = new HashSet<>();
        Set<AnnotatedField<T>> fields = new HashSet<>();

        Visit.ClassHierarchy.of(clazz, new RecursiveVisitor() {
            @Override
            public <T> void visit(Class<T> clazz) {
                if (clazz == null || clazz==Object.class) {
                    return;
                }
                Arrays.stream(clazz.getDeclaredMethods())
                        .filter(m -> !m.isSynthetic())
                        .map(m -> new AnnotatedMethod<>(m, allAnnotations, AnnotatedType.this))
                        .forEach(methods::add);

                Arrays.stream(clazz.getDeclaredFields())
                        .filter(f -> !f.isSynthetic())
                        .map(f -> new AnnotatedField<>(f, allAnnotations.get(f), AnnotatedType.this))
                        .forEach(fields::add);

                super.visit(clazz);
            }
        });
        this.methods = Collections.unmodifiableSet(methods);
        this.fields = Collections.unmodifiableSet(fields);
    }

    public static <T> AnnotatedType<T> of(Class<T> clazz) {
        final TypeVariableResolver typeResolver = TypeVariableResolver.withKnownTypesOf(clazz);
        return new AnnotatedType<>(clazz, Collector.collectAllAnnotations(clazz), clazz,
                                   typeResolver.resolvedTypeClosure(clazz));
    }

    public static <T> AnnotatedType<T> of(javax.enterprise.inject.spi.AnnotatedType<T> annotatedType) {
        return new AnnotatedType<>(annotatedType.getJavaClass(), Collector.collectAllAnnotations(annotatedType),
                                   annotatedType.getBaseType(), annotatedType.getTypeClosure());
    }

    @Override
    public Class<T> getJavaClass() {
        return annotatedElement;
    }

    @Override
    public Set<javax.enterprise.inject.spi.AnnotatedConstructor<T>> getConstructors() {
        return (Set) constructors;
    }

    @Override
    public Set<javax.enterprise.inject.spi.AnnotatedMethod<? super T>> getMethods() {
        return (Set) methods;
    }

    @Override
    public Set<javax.enterprise.inject.spi.AnnotatedField<? super T>> getFields() {
        return (Set) fields;
    }
}
