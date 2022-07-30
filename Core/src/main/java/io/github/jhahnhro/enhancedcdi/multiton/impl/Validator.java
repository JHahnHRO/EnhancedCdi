package io.github.jhahnhro.enhancedcdi.multiton.impl;

import io.github.jhahnhro.enhancedcdi.multiton.BeanParameter;
import io.github.jhahnhro.enhancedcdi.multiton.ParametrizedAnnotation;
import io.github.jhahnhro.enhancedcdi.multiton.ParametrizedBean;
import io.github.jhahnhro.enhancedcdi.multiton.exception.BeanParameterOutsideParametrizedBeanClassException;
import io.github.jhahnhro.enhancedcdi.multiton.exception.InvalidBeanParameterException;
import io.github.jhahnhro.enhancedcdi.multiton.exception.InvalidParametrizedAnnotationException;
import io.github.jhahnhro.enhancedcdi.multiton.exception.MissingBeanParameterException;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.config.spi.Converter;

import javax.enterprise.event.Observes;
import javax.enterprise.event.ObservesAsync;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.*;
import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Inject;
import java.lang.annotation.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class Validator {

    private static final Predicate<Annotated> HAS_INJECT_ANNOTATION = a -> a.isAnnotationPresent(Inject.class);
    private static final Predicate<Annotated> HAS_BEAN_PARAMETER_ANNOTATION = a -> a.isAnnotationPresent(
            BeanParameter.class);

    private static final String USE_LITERAL_CLASS =
            "Use " + ParametrizedAnnotation.class.getCanonicalName() + "#literalClass()"
            + " to explicitly specify an appropriate class of annotation literals";
    public static final String BEAN_PARAMETER_INJECTION_POINTS_OF =
            BeanParameter.class.getCanonicalName() + " qualified injection point(s) of ";

    final Config appConfig;

    public Validator(Config appConfig) {

        this.appConfig = appConfig;
    }


    //region Validate ParametrizedAnnotations

    /**
     * Validates all {@link ParametrizedAnnotation} annotations present on the given type itself, its fields, its
     * constructors, its (static and non-static) methods as well as all their parameters.
     *
     * @param type The type whose elements should be validated
     * @param <X>  The type of the bean.
     */
    public <X> void validateParametrizedAnnotations(AnnotatedType<X> type) {

        validateParametrizedAnnotations(type, ElementType.TYPE);

        type.getFields().forEach(this::validateParametrizedAnnotations);
        type.getConstructors().forEach(this::validateParametrizedAnnotations);
        type.getMethods().forEach(this::validateParametrizedAnnotations);
    }

    public <X> void validateParametrizedAnnotations(AnnotatedMethod<X> callable) {
        validateParametrizedAnnotations(callable, ElementType.METHOD);
        callable.getParameters().forEach(param -> validateParametrizedAnnotations(param, ElementType.PARAMETER));
    }

    private <X> void validateParametrizedAnnotations(AnnotatedField<X> field) {
        validateParametrizedAnnotations(field, ElementType.FIELD);
    }

    private <X> void validateParametrizedAnnotations(AnnotatedConstructor<X> callable) {
        validateParametrizedAnnotations(callable, ElementType.CONSTRUCTOR);
        callable.getParameters().forEach(param -> validateParametrizedAnnotations(param, ElementType.PARAMETER));
    }

    private void validateParametrizedAnnotations(Annotated element, ElementType elementType) {
        element.getAnnotations(ParametrizedAnnotation.class)
                .forEach(parametrizedAnnotation -> validateParametrizedAnnotation(parametrizedAnnotation, element,
                                                                                  elementType));
    }

    private void validateParametrizedAnnotation(ParametrizedAnnotation parametrizedAnnotation, Annotated element,
                                                ElementType elementType) {
        Class<? extends Annotation> annotationType = parametrizedAnnotation.value();
        final String givenInParametrizedAnnotationOnElement =
                " given in the annotation " + parametrizedAnnotation + " on " + element;
        // 1. verify that the "annotation type" truly is an annotation type to prevent evil reflection stuff
        if (!annotationType.isAnnotation()) {
            throw new InvalidParametrizedAnnotationException(
                    "The type " + annotationType + givenInParametrizedAnnotationOnElement + " is not an "
                    + "annotation type");
        }

        // 2. verify that the annotation type is applicable to the element it is supposed to be on
        // 2a. @Target matches the element type
        verifyAnnotationTarget(annotationType, elementType,
                               "The annotation type " + givenInParametrizedAnnotationOnElement);
        // 2b. @Retention matches
        verifyRetentionPolicy(annotationType, "The annotation type " + givenInParametrizedAnnotationOnElement);

        // 3. verify that the "literal type" is a compatible annotation literal type
        verifyLiteralClass(parametrizedAnnotation, givenInParametrizedAnnotationOnElement);
    }

    private void verifyLiteralClass(ParametrizedAnnotation parametrizedAnnotation,
                                    String givenInParametrizedAnnotationOnElement) {
        Class<? extends Annotation> annotationType = parametrizedAnnotation.value();

        Class<? extends Annotation> literalType;
        String errMsgPrefix;
        if (AnnotationLiteral.class.equals(parametrizedAnnotation.literalType())) {
            // default value => an inner class named "Literal" is assumed.
            literalType = findLiteralInnerClass(annotationType);
            errMsgPrefix = literalType.toString();
        } else {
            // non-default value => use the given class
            literalType = parametrizedAnnotation.literalType();
            errMsgPrefix = literalType.toString() + givenInParametrizedAnnotationOnElement;
        }

        if (!annotationType.isAssignableFrom(literalType) || !AnnotationLiteral.class.isAssignableFrom(literalType)) {
            throw new InvalidParametrizedAnnotationException(
                    errMsgPrefix + " is not the type of an annotation literal. " + USE_LITERAL_CLASS);
        }
    }


    private <A extends Annotation> Class<? extends A> findLiteralInnerClass(Class<A> annotationType) {
        for (Class<?> innerClass : annotationType.getDeclaredClasses()) {
            if ("Literal".equalsIgnoreCase(innerClass.getSimpleName())) {
                if (annotationType.isAssignableFrom(innerClass)) {
                    return innerClass.asSubclass(annotationType);
                } else {
                    throw new InvalidParametrizedAnnotationException(
                            "The inner class 'Literal' in " + annotationType + " is not a subtype of " + annotationType
                            + ". " + USE_LITERAL_CLASS);
                }
            }
        }
        throw new InvalidParametrizedAnnotationException(
                "There is no inner class 'Literal' in " + annotationType + ". " + USE_LITERAL_CLASS);
    }

    private void verifyRetentionPolicy(Class<? extends Annotation> annotationType, String errMsgPrefix) {
        RetentionPolicy retention = Optional.ofNullable(annotationType.getAnnotation(Retention.class))
                .map(Retention::value)
                .orElse(RetentionPolicy.CLASS);
        if (!RetentionPolicy.RUNTIME.equals(retention)) {
            throw new InvalidParametrizedAnnotationException(errMsgPrefix + " is not a runtime annotation.");
        }
    }


    private void verifyAnnotationTarget(Class<? extends Annotation> annotationType, ElementType requiredElementType,
                                        String errMsgPrefix) {
        ElementType[] allowedTargets = Optional.ofNullable(annotationType.getAnnotation(Target.class))
                .map(Target::value)
                // Without @Target the annotation is allowed everywhere (except on type parameter declarations which
                // doesn't matter here)
                .orElse(ElementType.values());
        if (Arrays.stream(allowedTargets).noneMatch(Predicate.isEqual(requiredElementType))) {
            throw new DefinitionException(errMsgPrefix + " is not applicable to that element");
        }
    }
    //endregion

    //region Validating InjectionPoints

    /**
     * Checks whether the given injection point belongs to a parametrized bean and returns the annotated element that
     * defines the parametrized bean, i.e. the {@link AnnotatedMethod} that defines the {@link Produces producer method}
     * that has the given injection point as parameter or the {@link AnnotatedType} that contains the field, constructor
     * or method parameter.
     *
     * @param injectionPoint the injection point to check
     * @return the annotated element that defines the parametrized bean.
     */
    private Annotated validateBeanParameterDeclaration(InjectionPoint injectionPoint) {

        if (injectionPoint == null // happens when a (non-)contextual instance is resolved manually with BeanManager
            || injectionPoint.getBean() == null // this happens if the injection point does not belong to any CDI-bean.
            // For example if it is inside a servlet, a message-driven EJB, JAX-RS endpoint, or something else that
            // is an injection *target* but not itself a bean. Can also happen when using
            // BeanManager#createInjectionPoint, CDI.current().select(...).borrow(), or when using Unmanaged
        ) {
            throw new BeanParameterOutsideParametrizedBeanClassException();
        }


        Annotated annotated = injectionPoint.getAnnotated();
        final Annotated annotatedElementThatDefinesTheBean;
        if (annotated instanceof AnnotatedField) {
            annotatedElementThatDefinesTheBean = ((AnnotatedField<?>) annotated).getDeclaringType();
        } else if (annotated instanceof AnnotatedParameter) {
            AnnotatedCallable<?> declaringCallable = ((AnnotatedParameter<?>) annotated).getDeclaringCallable();
            if (declaringCallable.isAnnotationPresent(Produces.class) && declaringCallable.isAnnotationPresent(
                    ParametrizedBean.class)) {
                annotatedElementThatDefinesTheBean = declaringCallable;
            } else {
                annotatedElementThatDefinesTheBean = declaringCallable.getDeclaringType();
            }
        } else {
            throw new IllegalArgumentException(
                    "Encountered an injection point which is neither a field nor a parameter");
        }

        if ( // this should not happen, unless someone manually implemented InjectionPoint
                annotatedElementThatDefinesTheBean == null
                // somebody tries to inject a bean parameter into a bean that's not parametrized
                || !annotatedElementThatDefinesTheBean.isAnnotationPresent(ParametrizedBean.class)) {
            throw new BeanParameterOutsideParametrizedBeanClassException();
        }
        return annotatedElementThatDefinesTheBean;
    }

    private void validateMatchingType(InjectionPoint injectionPoint, WithParameter parameterMarker) {
        if (!parameterMarker.parameterClass().equals(injectionPoint.getType())) {
            throw new InvalidBeanParameterException(
                    injectionPoint + " does not have the type with which its declaring bean was parametrized");
        }
    }

    private Type validateInjectionPointsDefineUniqueType(Annotated annotatedElement,
                                                         Set<? extends Annotated> injectionPoints) {

        Set<Type> types = injectionPoints.stream().map(Annotated::getBaseType).collect(Collectors.toSet());
        if (types.isEmpty()) {
            throw new MissingBeanParameterException(
                    "There are no " + BEAN_PARAMETER_INJECTION_POINTS_OF + annotatedElement);
        } else if (types.size() > 1) {
            throw new InvalidBeanParameterException(
                    "The " + BEAN_PARAMETER_INJECTION_POINTS_OF + annotatedElement + " do not all have the same type.");
        }

        return types.iterator().next();
    }

    private <P> Class<P> validateParameterType(Annotated annotatedElement, Type parameterType) {
        if (!(parameterType instanceof final Class<?> parameterClass)) {
            throw new InvalidBeanParameterException(
                    "The " + BEAN_PARAMETER_INJECTION_POINTS_OF + annotatedElement + " do not have a class as type");
        }

        if (parameterClass.getTypeParameters().length > 0) {
            throw new InvalidBeanParameterException(
                    "The " + BEAN_PARAMETER_INJECTION_POINTS_OF + annotatedElement + " have a raw class as type");
        }

        if (this.appConfig.getConverter(parameterClass).isEmpty()) {
            throw new InvalidBeanParameterException(
                    "No Converter was found for the type of the " + BEAN_PARAMETER_INJECTION_POINTS_OF
                    + annotatedElement);
        }

        //noinspection unchecked
        return (Class<P>) parameterClass;
    }

    private <P> String validateInjectionPointsDefineUniqueConfigProperty(Annotated annotatedElement,
                                                                         Class<P> parameterClass, Set<?
            extends Annotated> injectionPoints) {

        Set<String> names = injectionPoints.stream()
                .map(annotated -> annotated.getAnnotation(ConfigProperty.class))
                .filter(Objects::nonNull)
                .map(ConfigProperty::name)
                .filter(Predicate.not(String::isEmpty))
                .collect(Collectors.toSet());

        if (names.isEmpty()) {
            if (parameterClass.isEnum()) {
                return null;
            }
            throw new InvalidBeanParameterException(
                    "There is no " + ConfigProperty.class.getCanonicalName() + " qualifier on the "
                    + BEAN_PARAMETER_INJECTION_POINTS_OF + annotatedElement
                    + " or no config property name has been defined. This is only allowed if the type is an enum "
                    + "class.");
        } else if (names.size() > 1) {
            throw new InvalidBeanParameterException("The " + BEAN_PARAMETER_INJECTION_POINTS_OF + annotatedElement
                                                    + " do not all have the same config property name.");
        }

        return names.iterator().next();
    }

    private <T> Set<Annotated> getBeanParameterInjectionPoints(AnnotatedType<T> annotatedType) {
        Set<Annotated> beanParameterElements = new HashSet<>();

        // Find all elements of the class that are @BeanParameter injection points...

        // 1. Fields
        annotatedType.getFields()
                .stream()
                .filter(HAS_INJECT_ANNOTATION)
                .filter(HAS_BEAN_PARAMETER_ANNOTATION)
                .forEach(beanParameterElements::add);
        // 2. Parameters of the bean constructor if that is not the default constructor
        annotatedType.getConstructors()
                .stream()
                .filter(HAS_INJECT_ANNOTATION)
                .flatMap(ctor -> ctor.getParameters().stream())
                .filter(HAS_BEAN_PARAMETER_ANNOTATION)
                .forEach(beanParameterElements::add);
        // 3. Parameters of initializer methods
        annotatedType.getMethods()
                .stream()
                .filter(HAS_INJECT_ANNOTATION)
                .flatMap(method -> method.getParameters().stream())
                .filter(HAS_BEAN_PARAMETER_ANNOTATION)
                .forEach(beanParameterElements::add);

        return beanParameterElements;
    }

    private <X> Set<AnnotatedParameter<X>> getBeanParameterInjectionPoints(AnnotatedMethod<X> annotatedMethod) {
        return annotatedMethod.getParameters()
                .stream()
                .filter(p -> !p.isAnnotationPresent(Observes.class) && !p.isAnnotationPresent(ObservesAsync.class)
                             && !p.isAnnotationPresent(Disposes.class))
                .collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * Checks whether the given {@link AnnotatedType} is a valid definition of a parametrized bean.
     *
     * @param type           The original annotated type that defines (possibly) a parametrized bean
     * @param beanAttributes the {@link BeanAttributes} the type would have if it were not a parametrized bean. Will be
     *                       passed through to the returned BeanClassData.
     * @param <P>            the class of the bean parameter
     * @param <T>            the bean class
     * @return Incomplete {@link BeanClassData} for the parameterized bean.
     */
    public <P, T> BeanClassData<P, T> validateParametrizedBeanClass(AnnotatedType<T> type,
                                                                    BeanAttributes<T> beanAttributes) {
        validateParametrizedAnnotations(type);

        Set<Annotated> beanParameterInjectionPoints = getBeanParameterInjectionPoints(type);

        Type parameterType = validateInjectionPointsDefineUniqueType(type, beanParameterInjectionPoints);
        Class<P> parameterClass = validateParameterType(type, parameterType);
        String configProperty = validateInjectionPointsDefineUniqueConfigProperty(type, parameterClass,
                                                                                  beanParameterInjectionPoints);

        return new BeanClassData<>(type, beanAttributes, parameterClass, configProperty, appConfig);
    }

    /**
     * Checks whether the given {@link AnnotatedMethod} is a valid definition of a parametrized bean.
     *
     * @param bean           The managed bean that contains {@code method}
     * @param method         The original method that defines (possibly) a parametrized bean
     * @param beanAttributes the {@link BeanAttributes} the producer method would have if it were not a parametrized
     *                       bean. Will be passed through to the returned {@link ProducerMethodData}.
     * @param <P>            the class of the bean parameter
     * @param <T>            the bean type (=return type) of the producer method
     * @param <X>            the bean class
     * @param <Y>            the super class of the bean class in which the producer method is declared
     * @return Incomplete {@link ProducerMethodData} for the parameterized bean.
     */
    public <P, T, X extends Y, Y> ProducerMethodData<P, T, X, Y> validateParametrizedProducerMethod(Bean<X> bean,
                                                                                                    AnnotatedMethod<Y> method, BeanAttributes<T> beanAttributes) {
        validateParametrizedAnnotations(method);


        Set<AnnotatedParameter<Y>> methodParameters = getBeanParameterInjectionPoints(method);
        // special rule for parametrized producer methods inside parametrized classes
        WithParameter annotation = method.getDeclaringType().getAnnotation(WithParameter.class);
        if (annotation != null) {
            methodParameters.removeIf(param -> param.getBaseType().equals(annotation.parameterClass()));
        }

        Type beanParameterType = validateInjectionPointsDefineUniqueType(method, methodParameters);
        Class<P> beanParameterClass = validateParameterType(method, beanParameterType);
        String configProperty = validateInjectionPointsDefineUniqueConfigProperty(method, beanParameterClass,
                                                                                  methodParameters);

        return new ProducerMethodData<>(bean, method, beanAttributes, beanParameterClass, configProperty, appConfig);
    }

    /**
     * Validates that the given injection point satisfies most of the conditions @{@link BeanParameter @BeanParameter}
     * qualified injection points must satisfy, more precisely the following conditions are validated:
     * <ul>
     *     <li>The injection point belongs to a bean annotated with {@link ParametrizedBean @ParametrizedBean}</li>
     *     <li>The required type of the injection point must be a concrete class without type parameters</li>
     *     <li>A {@link org.eclipse.microprofile.config.spi.Converter} for that type must exist</li>
     *     <li>The required type of the injection point must match the parameter class with which either the
     *     declaring parametrized bean or the parametrized bean containing the parametrized producer method is
     *     declared.</li>
     * </ul>
     *
     * @param injectionPoint the {@link InjectionPoint} to validate
     * @return bean parameter that should be injected into this injection point.
     */
    public <P> P validateBeanParameterInjectionPoint(InjectionPoint injectionPoint) {
        final Annotated declaration = validateBeanParameterDeclaration(injectionPoint);
        validateParameterType(declaration, injectionPoint.getType());

        final WithParameter parameterMarker = validateAgainstParameterMarker(injectionPoint, declaration);

        return validateParameterCanBeConverted(parameterMarker);
    }

    private <P> P validateParameterCanBeConverted(WithParameter parameterMarker) {
        @SuppressWarnings("OptionalGetWithoutIsPresent") // existence was validated before
        final Converter<P> converter = appConfig.getConverter((Class<P>) parameterMarker.parameterClass()).get();

        return converter.convert(parameterMarker.stringRepresentation());
    }

    private WithParameter validateAgainstParameterMarker(InjectionPoint injectionPoint, Annotated declaration) {
        WithParameter parameterMarker = declaration.getAnnotation(WithParameter.class);
        if (parameterMarker == null) {
            throw new BeanParameterOutsideParametrizedBeanClassException();
        }

        if (declaration instanceof AnnotatedType) {
            validateMatchingType(injectionPoint, parameterMarker);
        } else if (declaration instanceof AnnotatedMethod) {
            try {
                validateMatchingType(injectionPoint, parameterMarker);
            } catch (InvalidBeanParameterException ex) {
                // second chance: a parametrized producer method inside a parametrized bean class can have a
                // @BeanParameter injection whose type corresponds to the parametrized class, not the method
                AnnotatedType<?> declaringType = ((AnnotatedMethod<?>) declaration).getDeclaringType();
                if (declaringType.isAnnotationPresent(ParametrizedBean.class)) {
                    parameterMarker = declaringType.getAnnotation(WithParameter.class);
                    validateMatchingType(injectionPoint, parameterMarker);
                }
            }
        } else {
            throw new BeanParameterOutsideParametrizedBeanClassException();
        }
        return parameterMarker;
    }

    //endregion
}
