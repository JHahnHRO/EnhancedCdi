package io.github.jhahnhro.enhancedcdi.metadata;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.jhahnhro.enhancedcdi.types.Types;
import jakarta.enterprise.inject.spi.AnnotatedCallable;
import jakarta.enterprise.inject.spi.AnnotatedMember;
import jakarta.enterprise.inject.spi.AnnotatedType;

public class Collector {
    /**
     * Collects all annotations on
     * <ul>
     *     <li>the class itself, its superclasses and interfaces (and their superclasses/interfaces ...)</li>
     *     <li>declared and inherited fields</li>
     *     <li>declared and inherited methods and their parameters</li>
     *     <li>declared constructors, superclass constructors and their parameters</li>
     * </ul>
     *
     * @param clazz
     * @return
     */
    public static Map<AnnotatedElement, Set<Annotation>> collectAllAnnotations(Class<?> clazz) {
        final Stream<Class<?>> allSuperTypes = Stream.concat(Types.superClasses(clazz).stream(),
                                                             Types.superInterfaces(clazz).stream());
        return allSuperTypes.filter(superType -> !Object.class.equals(superType))
                .<AnnotatedElement>mapMulti((superType, downStream) -> {
                    downStream.accept(superType);

                    List<Executable> executables = new ArrayList<>();
                    Collections.addAll(executables, superType.getDeclaredConstructors());
                    Collections.addAll(executables, superType.getDeclaredMethods());
                    for (Executable executable : executables) {
                        downStream.accept(executable);
                        for (Parameter parameter : executable.getParameters()) {
                            downStream.accept(parameter);
                        }
                    }
                    for (Field field : superType.getDeclaredFields()) {
                        downStream.accept(field);
                    }
                })
                .collect(Collectors.toMap(Function.identity(), el -> Set.of(el.getAnnotations())));
    }

    public static <T> Map<AnnotatedElement, Set<Annotation>> collectAllAnnotations(AnnotatedType<T> annotatedType) {
        Map<AnnotatedElement, Set<Annotation>> result = new HashMap<>();

        result.put(annotatedType.getJavaClass(), annotatedType.getAnnotations());

        Set<AnnotatedMember<? super T>> members = new HashSet<>();
        members.addAll(annotatedType.getConstructors());
        members.addAll(annotatedType.getMethods());
        members.addAll(annotatedType.getFields());

        members.forEach(m -> result.put((AnnotatedElement) m.getJavaMember(), m.getAnnotations()));

        Set<AnnotatedCallable<? super T>> callables = new HashSet<>();
        callables.addAll(annotatedType.getConstructors());
        callables.addAll(annotatedType.getMethods());
        callables.forEach(callable -> callable.getParameters()
                .forEach(p -> result.put(p.getJavaParameter(), p.getAnnotations())));

        return result;
    }
}
