package io.github.jhahnhro.enhancedcdi.metadata;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

public abstract class AnnotatedCallableConfigurator<T, A extends javax.enterprise.inject.spi.AnnotatedCallable<T>,
        SELF extends AnnotatedCallableConfigurator<T, A, SELF>>
        extends AnnotatedMemberConfigurator<T, A, SELF> {

    final List<AnnotatedParameterConfigurator<T, A>> parameters;

    AnnotatedCallableConfigurator(A original, AnnotatedTypeConfigurator<T> declaringType) {
        super(original, declaringType);
        this.parameters = original.getParameters()
                .stream()
                .map(parameter -> new AnnotatedParameterConfigurator<>(parameter, self()))
                .toList();
    }

    public List<AnnotatedParameterConfigurator<T, A>> getParameters() {
        return parameters;
    }

    public List<javax.enterprise.inject.spi.configurator.AnnotatedParameterConfigurator<T>> params() {
        return (List) parameters;
    }

    public Stream<javax.enterprise.inject.spi.configurator.AnnotatedParameterConfigurator<T>> filterParams(Predicate<javax.enterprise.inject.spi.AnnotatedParameter<T>> predicate) {
        return params().stream().filter(p -> predicate.test(p.getAnnotated()));
    }

    @Override
    public SELF reset() {
        parameters.forEach(AnnotatedElementConfigurator::reset);
        return super.reset();
    }
}
