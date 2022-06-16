package io.github.jhahnhro.enhancedcdi.multiton.impl;

import io.github.jhahnhro.enhancedcdi.multiton.ParametrizedAnnotation;
import io.github.jhahnhro.enhancedcdi.multiton.testData.Color;
import io.github.jhahnhro.enhancedcdi.multiton.testData.trueBeans.SomeOtherQualifier;
import io.github.jhahnhro.enhancedcdi.multiton.testData.validation.AnnotationsWithFailingFactory;
import io.github.jhahnhro.enhancedcdi.multiton.testData.validation.AnnotationsWithValidFactories;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.enterprise.inject.IllegalProductException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

class AnnotationLiteralRepositoryTest {

    private AnnotationLiteralRepository<Color> literalRepository;

    private static List<Arguments> annotationClassesWithFailingFactory () {
        List<Arguments> arguments = new ArrayList<>();

        for (Class<?> cls : AnnotationsWithFailingFactory.class.getDeclaredClasses()) {
            arguments.add(Arguments.of(cls, Color.RED));
            if (cls.getSimpleName().endsWith("Always")) {
                arguments.add(Arguments.of(cls, Color.GREEN));
                arguments.add(Arguments.of(cls, Color.BLUE));
            }
        }
        return arguments;
    }

    private static List<Arguments> annotationClassesWithValidFactories () {
        List<Arguments> arguments = new ArrayList<>();

        for (Class<?> cls : AnnotationsWithValidFactories.class.getDeclaredClasses()) {
            for(Color color : Color.values()) {
                arguments.add(Arguments.of(cls, color));
            }
        }
        return arguments;
    }

    @BeforeEach
    void setUp () {
        this.literalRepository = new AnnotationLiteralRepository<>(Color.class);
    }

    @ParameterizedTest(name = "annotationClass = {0}, color = {1}")
    @MethodSource("annotationClassesWithFailingFactory")
    void givenAnnotationClassWithFailingFactory_whenGetLiteral_thenThrowDefinitionException (
            Class<? extends Annotation> annotationClass, Color parameter) {

        Assertions.assertThatThrownBy(
                () -> literalRepository.getLiteral(new ParametrizedAnnotationLiteral(annotationClass), parameter))
                .isInstanceOf(IllegalProductException.class);
    }


    @ParameterizedTest(name = "annotationClass = {0}, color = {1}")
    @MethodSource("annotationClassesWithValidFactories")
    void givenAnnotationWithCorrectLiteralInnerClass_whenGetLiteral_thenSucceed (
            Class<? extends Annotation> annotationsClass, Color parameter)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Optional<Annotation> literal =
                literalRepository.getLiteral(new ParametrizedAnnotationLiteral(annotationsClass), parameter);

        Assertions.assertThat(literal).isPresent().get().isInstanceOf(annotationsClass);
        Assertions.assertThat(annotationsClass.getMethod("color").invoke(literal.get())).isEqualTo(parameter);
    }

    @Test
    void givenParametrizedAnnotationWithDifferentInput_whenGetAnnotationLiteral_thenReturnEmpty(){
        ParametrizedAnnotation parametrizedAnnotation = new ParametrizedAnnotationLiteral(SomeOtherQualifier.class);
        Optional<Annotation> literal = literalRepository.getLiteral(parametrizedAnnotation, Color.BLUE);
        Assertions.assertThat(literal).isEmpty();
    }
}