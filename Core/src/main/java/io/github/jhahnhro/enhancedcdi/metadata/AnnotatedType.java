package io.github.jhahnhro.enhancedcdi.metadata;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.jhahnhro.enhancedcdi.types.TypeVariableResolver;
import io.github.jhahnhro.enhancedcdi.types.Types;

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

        Set<AnnotatedMethod<T>> _methods = new HashSet<>();
        Set<AnnotatedField<T>> _fields = new HashSet<>();

        Stream.concat(Types.superClasses(clazz).stream(), Types.superInterfaces(clazz).stream())
                .filter(type -> !Object.class.equals(type))
                .forEach(superType -> {
                    Arrays.stream(superType.getDeclaredMethods())
                            .filter(m -> !m.isSynthetic())
                            .map(m -> new AnnotatedMethod<>(m, allAnnotations, AnnotatedType.this))
                            .forEach(_methods::add);

                    Arrays.stream(superType.getDeclaredFields())
                            .filter(f -> !f.isSynthetic())
                            .map(f -> new AnnotatedField<>(f, allAnnotations.get(f), AnnotatedType.this))
                            .forEach(_fields::add);
                });
        this.methods = Collections.unmodifiableSet(_methods);
        this.fields = Collections.unmodifiableSet(_fields);
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
