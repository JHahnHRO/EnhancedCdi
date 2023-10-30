package io.github.jhahnhro.enhancedcdi.multiton.testData.validation;

import javax.enterprise.event.Observes;
import javax.enterprise.event.ObservesAsync;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import io.github.jhahnhro.enhancedcdi.multiton.BeanParameter;
import io.github.jhahnhro.enhancedcdi.multiton.ParametrizedAnnotation;
import io.github.jhahnhro.enhancedcdi.multiton.ParametrizedBean;
import io.github.jhahnhro.enhancedcdi.multiton.testData.Color;

@SuppressWarnings("unused")
public class BeansWithValidAnnotations {

    public static class EmptyClasses {

        @ParametrizedAnnotation(AnnotationsWithValidFactories.LiteralWithConstructor.class)
        public static class BeanWithAnnotationWithLiteralWithConstructor {}

        @ParametrizedAnnotation(AnnotationsWithValidFactories.LiteralWithOfMethod.class)
        public static class BeanWithAnnotationWithLiteralWithOfMethod {}

        @ParametrizedAnnotation(AnnotationsWithValidFactories.LiteralWithFactory.class)
        public static class BeanWithAnnotationWithLiteralWithFactory {}
    }

    public static class ClassesWithBeanParameter {


        @ParametrizedBean
        public static class FieldInjection {
            @Inject
            @BeanParameter
            Color myColor;
        }

        @ParametrizedBean
        public static class ConstructorInjection {
            @Inject
            ConstructorInjection(@BeanParameter Color myColor) {
            }
        }

        @ParametrizedBean
        public static class InitializerInjection {
            @Inject
            void setMyColor(@BeanParameter Color myColor) {
            }
        }

        @ParametrizedBean
        public static class ProducerMethod {
            @Produces
            Object produceSomething(@BeanParameter Color myColor) {
                return null;
            }
        }

        @ParametrizedBean
        public static class DisposerMethod {
            void disposeSomething(@Disposes Object ignored, @BeanParameter Color myColor) {

            }
        }

        @ParametrizedBean
        public static class ObserverMethod {
            void observe(@Observes Object anything, @BeanParameter Color myColor) {}
        }

        @ParametrizedBean
        public static class AsyncObserverMethod {
            void observe(@ObservesAsync Object anything, @BeanParameter Color myColor) {}
        }


    }
}
