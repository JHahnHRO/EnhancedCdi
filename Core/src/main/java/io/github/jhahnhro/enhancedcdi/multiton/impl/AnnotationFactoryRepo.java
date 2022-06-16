package io.github.jhahnhro.enhancedcdi.multiton.impl;

import io.github.jhahnhro.enhancedcdi.multiton.ParametrizedAnnotation;
import io.github.jhahnhro.enhancedcdi.multiton.exception.InvalidParametrizedAnnotationException;

import javax.enterprise.util.AnnotationLiteral;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.reflect.Modifier.*;

/**
 * Stores constructors/factory methods for annotation literals that are parametrized by a given enum class.
 *
 * @param <P> The parameter class. There will be one annotation literal instantiated for each enum constant in E.
 */
class AnnotationFactoryRepo<P> {

    private final Map<ParametrizedAnnotation, Optional<Factory<P, Annotation>>> annotationFactories;
    private final Class<P> parameterClass;

    AnnotationFactoryRepo(Class<P> parameterClass) {
        this.parameterClass = parameterClass;
        this.annotationFactories = new HashMap<>();
    }

    Optional<Factory<P, Annotation>> getAnnotationFactory(ParametrizedAnnotation parametrizedAnnotation) {
        return annotationFactories.computeIfAbsent(parametrizedAnnotation, this::createFactory);
    }

    private Optional<Factory<P, Annotation>> createFactory(ParametrizedAnnotation parametrizedAnnotation) {
        Class<? extends Annotation> annotationType = parametrizedAnnotation.value();

        Class<? extends Annotation> literalType = AnnotationLiteral.class.equals(parametrizedAnnotation.literalType()) ?
                // default value => an inner class named "Literal" is assumed.
                getLiteralInnerClass(annotationType) :
                // non-default value => use the given class
                parametrizedAnnotation.literalType();

        return Optional.ofNullable((Factory<P, Annotation>) getFactory(literalType));
    }

    private <A extends Annotation> Class<? extends A> getLiteralInnerClass(Class<A> annotationType) {
        // when this method is called, we already verified that such a class exists in Validator
        //noinspection OptionalGetWithoutIsPresent
        return Arrays.stream(annotationType.getDeclaredClasses())
                .filter(cls -> cls.getSimpleName().equalsIgnoreCase("Literal")).findFirst().get()
                .asSubclass(annotationType);
    }


    private <L extends Annotation> Factory<P, L> getFactory(Class<L> literalClass) {

        Factory<P, L> factory = null;
        if (!isAbstract(literalClass.getModifiers())) {
            // 1. find public constructor
            factory = getFactoryFromConstructor(literalClass);
        }
        // 2. find public static factory method
        return factory != null ? factory : getFactoryFromStaticMethod(literalClass);
    }

    private <L extends Annotation> Factory<P, L> getFactoryFromStaticMethod(Class<L> literalClass) {

        // Step 0: find all public static methods with matching signature.
        Set<Method> matchingMethods = Arrays.stream(literalClass.getDeclaredMethods())
                .filter(m -> isPublic(m.getModifiers()) && isStatic(m.getModifiers()) && m.getParameterCount() == 1 && m
                        .getParameterTypes()[0].isAssignableFrom(parameterClass) && literalClass
                                     .isAssignableFrom(m.getReturnType())).collect(Collectors.toSet());

        // Step 1: A method called "of" has priority if it exists
        return matchingMethods.stream().filter(m -> "of".equals(m.getName())).findAny()
                // Step 2: As a fallback, if there is a unique method with another name, use that
                .or(() -> matchingMethods.stream().reduce((m1, m2) -> {
                    // if there is more than one method, throw
                    throw new InvalidParametrizedAnnotationException(
                            "More than one public static method in " + literalClass + " accepts arguments of type "
                            + parameterClass + " and returns " + literalClass);
                })).map(m -> getFactoryFromStaticMethod(literalClass, m)).orElse(null);
    }

    private <L> Factory<P, L> getFactoryFromStaticMethod(Class<L> clazz, Method staticMethod) {
        if (!staticMethod.trySetAccessible()) {
            throw new InvalidParametrizedAnnotationException(
                    staticMethod + " is not accessible and cannot be made accessible");
        }

        return new Factory<>(p -> {
            try {
                return clazz.cast(staticMethod.invoke(null, p));
            } catch (IllegalAccessException ex) {
                // this cannot happen in a sane environment: We have just made it accessible
                throw new AssertionError(ex);
            }
        }, staticMethod.toString());
    }

    private <L> Factory<P, L> getFactoryFromConstructor(Class<L> clazz) {

        Constructor<L> ctor;
        try {
            ctor = clazz.getConstructor(parameterClass);
        } catch (NoSuchMethodException e) {
            return null;
        }

        if (!ctor.trySetAccessible()) {
            throw new InvalidParametrizedAnnotationException(ctor + " is not accessible and cannot be made accessible");
        }

        return new Factory<>(p -> {
            try {
                return ctor.newInstance(p);
            } catch (InstantiationException | IllegalAccessException ex) {
                // this cannot happen in a sane environment: We have just made it accessible and we only call this
                // method if the class isn't abstract
                throw new AssertionError(ex);
            }
        }, ctor.toString());
    }

}