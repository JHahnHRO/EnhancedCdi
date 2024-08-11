package io.github.jhahnhro.enhancedcdi.metadata;

public class AnnotatedConstructorConfigurator<T> extends
                                                 AnnotatedCallableConfigurator<T,
                                                         jakarta.enterprise.inject.spi.AnnotatedConstructor<T>,
                                                         AnnotatedConstructorConfigurator<T>>
        implements jakarta.enterprise.inject.spi.configurator.AnnotatedConstructorConfigurator<T> {

    AnnotatedConstructorConfigurator(jakarta.enterprise.inject.spi.AnnotatedConstructor<T> original,
                                     AnnotatedTypeConfigurator<T> declaringType) {
        super(original, declaringType);
    }

    @Override
    public AnnotatedConstructor<T> build() {
        return declaringType.build().constructors.stream()
                .filter(c -> c.getJavaMember().equals(getAnnotated().getJavaMember()))
                .findAny()
                .orElseThrow();
    }

}
