package io.github.jhahnhro.enhancedcdi.metadata;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Set;

class AnnotatedMember<MEMBER extends java.lang.reflect.Member & java.lang.reflect.AnnotatedElement, X>
        extends AnnotatedElement<MEMBER> implements jakarta.enterprise.inject.spi.AnnotatedMember<X> {

    protected final AnnotatedType<X> declaringType;

    protected AnnotatedMember(MEMBER member, Set<Annotation> annotations, Type baseType, Set<Type> typeClosure,
                              AnnotatedType<X> declaringType) {
        super(member, annotations, baseType, typeClosure);
        if (!member.getDeclaringClass().isAssignableFrom(declaringType.getJavaClass())) {
            throw new IllegalArgumentException(
                    "The declaring class of the parameter 'member' must be a subtype of the underlying class of the "
                    + "parameter 'declaringType'. Instead 'member' has declaringClass="
                    + member.getDeclaringClass() + " and 'declaringType' has javaClass="
                    + declaringType.getJavaClass());
        }
        this.declaringType = declaringType;
    }

    @Override
    public MEMBER getJavaMember() {
        return annotatedElement;
    }

    @Override
    public boolean isStatic() {
        return Modifier.isStatic(annotatedElement.getModifiers());
    }

    @Override
    public AnnotatedType<X> getDeclaringType() {
        return declaringType;
    }
}
