package io.github.jhahnhro.enhancedcdi.multiton;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.DefinitionException;

import io.github.jhahnhro.enhancedcdi.metadata.AnnotatedElementConfigurator;
import io.github.jhahnhro.enhancedcdi.metadata.AnnotatedMethodConfigurator;
import io.github.jhahnhro.enhancedcdi.metadata.AnnotatedTypeConfigurator;
import io.github.jhahnhro.enhancedcdi.multiton.impl.AnnotationLiteralRepository;

public class AnnotationSpecializer<E> {

    private final AnnotationLiteralRepository<E> repo;

    public AnnotationSpecializer(Class<E> enumClass) {
        this.repo = new AnnotationLiteralRepository<>(enumClass);
    }


    /**
     * Specialises an annotated type to all its parametrized types. All {@link ParametrizedAnnotation} annotations at
     * the type itself, its fields, methods, constructors, as well as their parameters will be replaced by instances of
     * the specified {@link ParametrizedAnnotation#value() annotation type}.
     *
     * @param type An annotated type.
     * @param <T>  the type
     * @return A mapping from each enum constant to a fully specialised type.
     * @throws DefinitionException      If {@code type} does not have a
     * @throws IllegalArgumentException {@code type} is not a valid parametrized type or if one of the parametrized
     *                                  annotations can not be instantiated.
     * @throws NullPointerException     if {@code type} is null.
     */
    public <T> AnnotatedType<T> specializeAnnotatedType(AnnotatedType<T> type, E parameter) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(parameter);
        return configureParametrizedType(type, parameter).build();
    }


    <T> AnnotatedTypeConfigurator<T> configureParametrizedType(AnnotatedType<T> originalType, E parameter) {
        AnnotatedTypeConfigurator<T> configurator = new AnnotatedTypeConfigurator<>(originalType);

        Set<AnnotatedElementConfigurator<?, ?>> configurators = new HashSet<>();
        configurators.add(configurator);
        configurators.addAll(configurator.getConstructors());
        configurator.getConstructors().stream().flatMap(c -> c.getParameters().stream()).forEach(configurators::add);
        configurators.addAll(configurator.getMethods());
        configurator.getMethods().stream().flatMap(m -> m.getParameters().stream()).forEach(configurators::add);
        configurators.addAll(configurator.getFields());

        configurators.forEach(c -> replaceAnnotations(c, parameter));

        return configurator;
    }

    private void replaceAnnotations(AnnotatedElementConfigurator<?, ?> configurator, E parameter) {
        configurator.replaceIf(ann -> ann.annotationType().equals(ParametrizedAnnotation.class),
                               ann -> repo.getLiteral((ParametrizedAnnotation) ann, parameter).orElse(ann));
    }

    <X> AnnotatedMethodConfigurator<X> configureAnnotatedMethod(AnnotatedMethod<X> originalMethod, E parameter) {

        final AnnotatedTypeConfigurator<X> typeConfigurator = new AnnotatedTypeConfigurator<>(
                originalMethod.getDeclaringType());
        AnnotatedMethodConfigurator<X> methodConfigurator = typeConfigurator.getMethods()
                .stream()
                .filter(m -> m.getAnnotated().getJavaMember().equals(originalMethod.getJavaMember()))
                .findAny()
                .orElseThrow();

        Set<AnnotatedElementConfigurator<?, ?>> configurators = new HashSet<>();
        configurators.add(methodConfigurator);
        configurators.addAll(methodConfigurator.getParameters());

        configurators.forEach(c -> replaceAnnotations(c, parameter));

        return methodConfigurator;
    }
}
