package io.github.jhahnhro.enhancedcdi.multiton.impl;

import io.github.jhahnhro.enhancedcdi.multiton.exception.InvalidParametrizedAnnotationException;
import io.github.jhahnhro.enhancedcdi.multiton.impl.AnnotationFactoryRepo;
import io.github.jhahnhro.enhancedcdi.multiton.impl.ParametrizedAnnotationLiteral;
import io.github.jhahnhro.enhancedcdi.multiton.testData.validation.AnnotationsWithInvalidFactories;
import io.github.jhahnhro.enhancedcdi.multiton.testData.validation.AnnotationsWithValidFactories;
import io.github.jhahnhro.enhancedcdi.multiton.testData.Color;
import io.github.jhahnhro.enhancedcdi.multiton.testData.validation.AnnotationsWithoutFactories;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.annotation.Annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnnotationFactoryRepoTest {

    private AnnotationFactoryRepo<Color> annotationFactoryRepo;

    private static Class<?>[] annotationClassesWithoutValidLiteralInnerClass () {
        return AnnotationsWithoutFactories.class.getDeclaredClasses();
    }
    private static Class<?>[] annotationClassesWithInvalidLiteralInnerClass () {
        return AnnotationsWithInvalidFactories.class.getDeclaredClasses();
    }

    private static Class<?>[] annotationClassesWithValidLiteralInnerClass () {
        return AnnotationsWithValidFactories.class.getDeclaredClasses();
    }

    @BeforeEach
    void setUp () {
        annotationFactoryRepo = new AnnotationFactoryRepo<>(Color.class);
    }

    @ParameterizedTest(name = "[{index}] : annotationClass = {0}")
    @MethodSource("annotationClassesWithoutValidLiteralInnerClass")
    void givenAnnotationClassWithoutCorrectLiteralInnerClass_whenGetAnnotationFactory_thenReturnEmptyOptional (
            Class<? extends Annotation> annotationClass) {

        ParametrizedAnnotationLiteral literal = new ParametrizedAnnotationLiteral(annotationClass);
        assertThat(annotationFactoryRepo.getAnnotationFactory(literal)).isEmpty();
    }

    @ParameterizedTest(name = "[{index}] : annotationClass = {0}")
    @MethodSource("annotationClassesWithInvalidLiteralInnerClass")
    void givenAnnotationClassWithInvalidLiteralInnerClass_whenGetAnnotationFactory_thenThrowDefinitionException (
            Class<? extends Annotation> annotationClass) {

        ParametrizedAnnotationLiteral literal = new ParametrizedAnnotationLiteral(annotationClass);
        Assertions.assertThatThrownBy(() -> annotationFactoryRepo.getAnnotationFactory(literal))
                .isInstanceOf(InvalidParametrizedAnnotationException.class);
    }

    @ParameterizedTest(name ="[{index}] : annotationClass = {0}")
    @MethodSource("annotationClassesWithValidLiteralInnerClass")
    void givenAnnotationWithCorrectLiteralInnerClass_whenGetAnnotationFactory_thenSucceed(Class<? extends Annotation> annotationsClass){
        annotationFactoryRepo.getAnnotationFactory(new ParametrizedAnnotationLiteral(annotationsClass));
    }

}