package io.github.jhahnhro.enhancedcdi.multiton.impl;

import io.github.jhahnhro.enhancedcdi.multiton.ParametrizedAnnotation;

import javax.enterprise.inject.IllegalProductException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class AnnotationLiteralRepository<P> {

    private final AnnotationFactoryRepo<P> factoryCache;
    private final Map<ParametrizedAnnotation, Map<P, Annotation>> annotationRepo;

    public AnnotationLiteralRepository(Class<P> parameterClass) {
        this.factoryCache = new AnnotationFactoryRepo<>(parameterClass);
        this.annotationRepo = new HashMap<>();
    }

    public Optional<Annotation> getLiteral(ParametrizedAnnotation pa, P parameter) {
        return Optional.ofNullable(getLiterals(pa, Collections.singleton(parameter)).get(parameter));
    }

    private Map<P, Annotation> getLiterals(ParametrizedAnnotation pa, Collection<P> parameters) {
        Map<P, Annotation> computedAnnotations = annotationRepo.computeIfAbsent(pa, __ -> new HashMap<>());
        List<P> parametersThatNeedComputing = parameters.stream()
                .filter(Predicate.not(computedAnnotations::containsKey)).collect(Collectors.toList());
        Map<P, Annotation> newAnnotations = createLiterals(pa, parametersThatNeedComputing);
        computedAnnotations.putAll(newAnnotations);

        Map<P, Annotation> result = new HashMap<>();
        for (P parameter : parameters) {
            Annotation computedAnnotation = computedAnnotations.get(parameter);
            if (computedAnnotation != null) {
                result.put(parameter, computedAnnotation);
            }
        }
        return result;
    }

    private Map<P, Annotation> createLiterals(ParametrizedAnnotation pa, Collection<P> parameters) {
        Optional<Factory<P, Annotation>> maybeFactory = factoryCache.getAnnotationFactory(pa);
        if (maybeFactory.isEmpty()) {
            return Collections.emptyMap();
        }

        Factory<P, Annotation> factory = maybeFactory.get();

        String msg = "The parametrized annotation " + pa + " could not be instantiated with the configured factory "
                     + factory.desc + " for the parameter value ";
        Map<P, Annotation> annotations = new HashMap<>();
        for (P parameter : parameters) {
            try {
                Annotation annotation = factory.call.apply(parameter);
                if (annotation == null) {
                    throw new IllegalProductException(msg + parameter + ", because the factory returned null");
                } else {
                    annotations.put(parameter, annotation);
                }
            } catch (InvocationTargetException e) {
                throw new IllegalProductException(msg + parameter + ", because the factory threw an exception", e);
            }
        }

        return annotations;
    }
}
