package io.github.jhahnhro.enhancedcdi.metadata;

import io.github.jhahnhro.enhancedcdi.reflection.Visit;
import io.github.jhahnhro.enhancedcdi.reflection.Visit.ClassHierarchy.RecursiveVisitor;

import javax.enterprise.inject.spi.AnnotatedCallable;
import javax.enterprise.inject.spi.AnnotatedMember;
import javax.enterprise.inject.spi.AnnotatedType;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Executable;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

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
        final Set<AnnotatedElement> elements = new HashSet<>();

        Visit.ClassHierarchy.of(clazz, new RecursiveVisitor() {
            @Override
            public <T> void visit(Class<T> clazz) {
                if (clazz == null) {
                    return;
                }
                elements.add(clazz);

                List<Executable> executables = new ArrayList<>();
                executables.addAll(List.of(clazz.getDeclaredConstructors()));
                executables.addAll(List.of(clazz.getDeclaredMethods()));
                for (Executable executable : executables) {
                    elements.add(executable);
                    elements.addAll(List.of(executable.getParameters()));
                }
                elements.addAll(List.of(clazz.getDeclaredFields()));
                super.visit(clazz);
            }
        });

        return elements.stream().collect(Collectors.toMap(Function.identity(), el -> Set.of(el.getAnnotations())));
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
