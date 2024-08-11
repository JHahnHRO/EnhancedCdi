package io.github.jhahnhro.enhancedcdi.multiton.impl;

import java.util.function.Function;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.InjectionPoint;

public class BeanParameterProducer<P> implements Function<Instance<Object>, P> {
    private final Validator validator;

    public BeanParameterProducer(Validator validator) {this.validator = validator;}

    @Override
    public P apply(Instance<Object> instance) {
        InjectionPoint injectionPoint = instance.select(InjectionPoint.class).get();

        return validator.validateBeanParameterInjectionPoint(injectionPoint);
    }

}
