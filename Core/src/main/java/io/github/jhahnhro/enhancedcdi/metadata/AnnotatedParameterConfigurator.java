package io.github.jhahnhro.enhancedcdi.metadata;

public class AnnotatedParameterConfigurator<T, CALLABLE extends javax.enterprise.inject.spi.AnnotatedCallable<T>>
        extends
        AnnotatedElementConfigurator<javax.enterprise.inject.spi.AnnotatedParameter<T>,
                AnnotatedParameterConfigurator<T, CALLABLE>>
        implements javax.enterprise.inject.spi.configurator.AnnotatedParameterConfigurator<T> {

    final AnnotatedCallableConfigurator<T, CALLABLE, ?> declaringCallable;

    AnnotatedParameterConfigurator(javax.enterprise.inject.spi.AnnotatedParameter<T> original,
                                             AnnotatedCallableConfigurator<T, CALLABLE, ?> declaringCallable) {
        super(original);
        this.declaringCallable = declaringCallable;
    }

    @Override
    public AnnotatedParameter<T> build() {
        return ((AnnotatedCallable<T>) declaringCallable.build()).parameters.get(getAnnotated().getPosition());
    }

}
