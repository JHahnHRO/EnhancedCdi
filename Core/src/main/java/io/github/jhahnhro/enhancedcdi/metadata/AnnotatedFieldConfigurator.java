package io.github.jhahnhro.enhancedcdi.metadata;

public class AnnotatedFieldConfigurator<T> extends
                                           AnnotatedMemberConfigurator<T,
                                                   jakarta.enterprise.inject.spi.AnnotatedField<T>,
                                                   AnnotatedFieldConfigurator<T>>
        implements jakarta.enterprise.inject.spi.configurator.AnnotatedFieldConfigurator<T> {

    AnnotatedFieldConfigurator(jakarta.enterprise.inject.spi.AnnotatedField<T> original,
                               AnnotatedTypeConfigurator<T> declaringType) {
        super(original, declaringType);
    }

    @Override
    public AnnotatedField<T> build() {
        return declaringType.build().fields.stream()
                .filter(f -> f.getJavaMember().equals(getAnnotated().getJavaMember()))
                .findAny()
                .orElse(null);
    }
}
