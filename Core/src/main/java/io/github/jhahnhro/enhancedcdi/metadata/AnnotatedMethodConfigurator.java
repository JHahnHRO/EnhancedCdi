package io.github.jhahnhro.enhancedcdi.metadata;

public class AnnotatedMethodConfigurator<T> extends
                                            AnnotatedCallableConfigurator<T,
                                                    jakarta.enterprise.inject.spi.AnnotatedMethod<T>,
                                                    AnnotatedMethodConfigurator<T>>
        implements jakarta.enterprise.inject.spi.configurator.AnnotatedMethodConfigurator<T> {

    AnnotatedMethodConfigurator(jakarta.enterprise.inject.spi.AnnotatedMethod<T> originalMethod,
                                AnnotatedTypeConfigurator<T> declaringType) {
        super(originalMethod, declaringType);
    }

    @Override
    public AnnotatedMethod<T> build() {
        return declaringType.build().methods.stream()
                .filter(m -> m.getJavaMember().equals(getAnnotated().getJavaMember()))
                .findAny()
                .orElseThrow();
    }

}
