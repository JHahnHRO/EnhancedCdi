package io.github.jhahnhro.enhancedcdi.multiton.impl;

import io.github.jhahnhro.enhancedcdi.multiton.BeanParameter;
import io.github.jhahnhro.enhancedcdi.multiton.testData.Color;
import io.github.jhahnhro.enhancedcdi.multiton.testData.trueBeans.ParametrizedBeanClass;
import io.github.jhahnhro.enhancedcdi.multiton.testData.validation.BeansWithInvalidAnnotations;
import io.github.jhahnhro.enhancedcdi.multiton.testData.validation.BeansWithValidAnnotations;
import io.github.jhahnhro.enhancedcdi.multiton.testData.validation.TheOneTrueBoolean;
import org.assertj.core.api.Assertions;
import org.eclipse.microprofile.config.Config;
import org.jboss.weld.junit.MockBean;
import org.jboss.weld.junit5.EnableWeld;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.enterprise.event.Observes;
import javax.enterprise.event.ObservesAsync;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.spi.AnnotatedParameter;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.DefinitionException;
import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Inject;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnableWeld
@ExtendWith(MockitoExtension.class)
class ValidatorTest {

    public static final Predicate<AnnotatedParameter<?>> IS_SPECIAL_PARAMETER = param ->
            param.isAnnotationPresent(Disposes.class) || param.isAnnotationPresent(Observes.class)
            || param.isAnnotationPresent(ObservesAsync.class);

    private static Class<?>[] getBeanClassesWithInvalidBeanParameter() {
        return BeansWithInvalidAnnotations.WithInvalidBeanParameterFields.class.getDeclaredClasses();
    }

    private static Class<?>[] getBeanClassesWithInconsistentBeanParameter() {
        return BeansWithInvalidAnnotations.WithNonUniqueBeanParameterInjectionPoints.class.getDeclaredClasses();
    }

    private static Class<?>[] getBeanClassesWithValidParametrizedAnnotationsAndBeanParameter() {
        return BeansWithValidAnnotations.ClassesWithBeanParameter.class.getDeclaredClasses();
    }

    private static Class<?>[] getBeanClassesWithValidParametrizedAnnotations() {
        return BeansWithValidAnnotations.EmptyClasses.class.getDeclaredClasses();
    }

    private static Class<?>[] getBeanClassesWithInvalidParametrizedAnnotations() {
        return BeansWithInvalidAnnotations.AnnotationOnType.class.getDeclaredClasses();
    }

    Validator validator;

    @WeldSetup
    WeldInitiator weld = WeldInitiator.from(
                    ParametrizedBeanClass.class) // not used, but container cannot be created without any beans or
            // extensions
            .addBeans(MockBean.builder() // for most @BeanParameter injection points
                              .addType(Color.class)
                              .addQualifier(new AnnotationLiteral<BeanParameter>() {})
                              .creating(Color.BLUE)
                              .build(), MockBean.builder() // for one of the illegal @BeanParameter injection points
                              .addType(TheOneTrueBoolean.class)
                              .addQualifier(new AnnotationLiteral<BeanParameter>() {})
                              .creating(TheOneTrueBoolean.FILE_NOT_FOUND)
                              .build(), MockBean.builder() // for one of the illegal injection points
                              .addType(Integer.class)
                              .addQualifier(new AnnotationLiteral<BeanParameter>() {})
                              .creating(42)
                              .build()
                      //
            ).build();

    @Mock
    Config appConfig;

    @Inject
    BeanManager beanManager;

    @BeforeEach
    void setUp() {
        validator = new Validator(appConfig);
    }


    @ParameterizedTest(name = "beanClass = {0}")
    @MethodSource("getBeanClassesWithInvalidParametrizedAnnotations")
    <T> void givenInvalidParametrizedAnnotation_whenValidate_thenThrowsDefinitionException(Class<T> beanClass) {
        assertThatThrownBy(
                        () -> validator.validateParametrizedAnnotations(beanManager.createAnnotatedType(beanClass)))
                .isInstanceOf(DefinitionException.class);
    }

    @ParameterizedTest(name = "beanClass = {0}")
    @MethodSource("getBeanClassesWithValidParametrizedAnnotations")
    <T> void givenValidParametrizedAnnotation_whenValidate_thenSucceed(Class<T> beanClass) {
        validator.validateParametrizedAnnotations(beanManager.createAnnotatedType(beanClass));
    }

    // TODO validateBeanParameterInjectionPoints
}