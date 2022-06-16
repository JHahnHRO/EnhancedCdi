package io.github.jhahnhro.enhancedcdi.multiton.impl;

import io.github.jhahnhro.enhancedcdi.multiton.ParametrizedAnnotation;

import javax.enterprise.util.AnnotationLiteral;
import java.lang.annotation.Annotation;

@SuppressWarnings({"unchecked", "rawtypes"})
class ParametrizedAnnotationLiteral extends AnnotationLiteral<ParametrizedAnnotation>
        implements ParametrizedAnnotation {

    private final Class<? extends Annotation> annotationClass;
    private final Class<? extends AnnotationLiteral> literalType;

    private ParametrizedAnnotationLiteral (Class<? extends Annotation> annotationClass,
                                           Class<? extends AnnotationLiteral> literalType) {
        this.annotationClass = annotationClass;
        this.literalType = literalType;
    }

    public ParametrizedAnnotationLiteral (Class<? extends Annotation> annotationClass) {
        this(annotationClass, AnnotationLiteral.class);
    }

    @Override
    public Class<? extends Annotation> value () {
        return annotationClass;
    }

    @Override
    public Class<? extends AnnotationLiteral<?>> literalType () {
        return (Class<? extends AnnotationLiteral<?>>) literalType;
    }
}
