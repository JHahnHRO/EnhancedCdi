package io.github.jhahnhro.enhancedcdi.metadata;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Set;

public class AnnotatedField<X> extends AnnotatedMember<Field, X>
        implements javax.enterprise.inject.spi.AnnotatedField<X> {
    protected AnnotatedField(Field field, Set<Annotation> annotations, AnnotatedType<X> declaringType) {
        super(field, annotations, declaringType.typeResolver.resolve(field.getGenericType()),
              declaringType.typeResolver.resolvedTypeClosure(field.getGenericType()), declaringType);
    }

    @Override
    public Field getJavaMember() {
        return annotatedElement;
    }
}
