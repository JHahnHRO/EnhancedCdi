package io.github.jhahnhro.enhancedcdi.metadata;

public abstract class AnnotatedMemberConfigurator<T, A extends javax.enterprise.inject.spi.AnnotatedMember<T>,
        SELF extends AnnotatedMemberConfigurator<T, A, SELF>>
        extends AnnotatedElementConfigurator<A, SELF> {

    final AnnotatedTypeConfigurator<T> declaringType;

    AnnotatedMemberConfigurator(A original, AnnotatedTypeConfigurator<T> declaringType) {
        super(original);
        this.declaringType = declaringType;
    }

}
