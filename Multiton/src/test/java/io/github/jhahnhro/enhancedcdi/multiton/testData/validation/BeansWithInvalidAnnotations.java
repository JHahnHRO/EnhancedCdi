package io.github.jhahnhro.enhancedcdi.multiton.testData.validation;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Inject;

import io.github.jhahnhro.enhancedcdi.multiton.BeanParameter;
import io.github.jhahnhro.enhancedcdi.multiton.ParametrizedAnnotation;
import io.github.jhahnhro.enhancedcdi.multiton.ParametrizedBean;
import io.github.jhahnhro.enhancedcdi.multiton.testData.Color;

@SuppressWarnings("unused")
public class BeansWithInvalidAnnotations {

    public static class Annotations {

        public @interface WithoutTargetAndRetention {}

        @Retention(RetentionPolicy.RUNTIME)
        @Target(ElementType.LOCAL_VARIABLE)
        public @interface WithInvalidTarget {}

        @Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER,
                ElementType.CONSTRUCTOR})
        public @interface WithoutRetention {}

        @Retention(RetentionPolicy.RUNTIME)
        public @interface WithoutLiteral {}

        @Retention(RetentionPolicy.RUNTIME)
        public @interface LiteralBothSupertypesMissing {
            class Literal {}
        }

        @Retention(RetentionPolicy.RUNTIME)
        public @interface LiteralDoesNotImplementAnnotation {
            class Literal extends AnnotationLiteral<LiteralDoesNotImplementAnnotation> {}
        }

        @Retention(RetentionPolicy.RUNTIME)
        public @interface LiteralDoesNotExtendAnnotationLiteral {
            abstract class Literal implements LiteralDoesNotExtendAnnotationLiteral {}
        }
    }

    public static class AnnotationOnType {

        @ParametrizedAnnotation(Annotation.class)
        public static class NotAnAnnotationType {}

        @ParametrizedAnnotation(Annotations.WithoutTargetAndRetention.class)
        public static class AnnotationWithoutTargetAndRetention {}

        @ParametrizedAnnotation(Annotations.WithInvalidTarget.class)
        public static class AnnotationWithInvalidTarget {}

        @ParametrizedAnnotation(Annotations.WithoutRetention.class)
        public static class AnnotationWithoutRetention {}

        @ParametrizedAnnotation(Annotations.WithoutLiteral.class)
        public static class AnnotationWithoutLiteral {}

        @ParametrizedAnnotation(Annotations.LiteralBothSupertypesMissing.class)
        public static class AnnotationWithLiteralBothSupertypesMissing {}

        @ParametrizedAnnotation(Annotations.LiteralDoesNotExtendAnnotationLiteral.class)
        public static class AnnotationWithLiteralDoesNotExtendAnnotationLiteral {}

        @ParametrizedAnnotation(Annotations.LiteralDoesNotImplementAnnotation.class)
        public static class AnnotationWithLiteralDoesNotImplementAnnotation {}
    }

    public static class WithInvalidBeanParameterFields {

        public static class BeanParameterNotInParametrizedClass{
            @Inject
            @BeanParameter
            private Color myColor;
        }


        @ParametrizedBean
        public static class BeanParameterWrongType {
            @Inject
            @BeanParameter
            private Integer myInt;
        }

    }

    public static class WithNonUniqueBeanParameterInjectionPoints {

        @ParametrizedBean
        public static class BeanParameterInconsistentType {
            @Inject
            @BeanParameter
            private Color myColor;

            @Inject
            @BeanParameter
            private TheOneTrueBoolean secondBeanParameter;

        }

        @ParametrizedBean
        public static class MultipleBeanParametersButWrongType {
            @Inject
            @BeanParameter
            private Integer firstBeanParameter;

            @Inject
            @BeanParameter
            private Integer secondBeanParameter;

        }

        @ParametrizedBean
        public static class WithoutBeanParameter {
        }
    }
}
