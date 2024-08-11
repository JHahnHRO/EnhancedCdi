package io.github.jhahnhro.enhancedcdi.metadata;

public class AnnotatedParameterConfigurator<T, CALLABLE extends jakarta.enterprise.inject.spi.AnnotatedCallable<T>>
        extends
        AnnotatedElementConfigurator<jakarta.enterprise.inject.spi.AnnotatedParameter<T>,
                AnnotatedParameterConfigurator<T, CALLABLE>>
        implements jakarta.enterprise.inject.spi.configurator.AnnotatedParameterConfigurator<T> {

    final AnnotatedCallableConfigurator<T, CALLABLE, ?> declaringCallable;

    AnnotatedParameterConfigurator(jakarta.enterprise.inject.spi.AnnotatedParameter<T> original,
                                   AnnotatedCallableConfigurator<T, CALLABLE, ?> declaringCallable) {
        super(original);
        this.declaringCallable = declaringCallable;
    }

    @Override
    public AnnotatedParameter<T> build() {
        return ((AnnotatedCallable<T>) declaringCallable.build()).parameters.get(getAnnotated().getPosition());
    }

}
