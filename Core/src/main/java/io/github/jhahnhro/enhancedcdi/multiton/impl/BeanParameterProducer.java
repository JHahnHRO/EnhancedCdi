package io.github.jhahnhro.enhancedcdi.multiton.impl;

import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.InjectionPoint;
import java.util.function.Function;

public class BeanParameterProducer<P> implements Function<Instance<Object>, P> {
    private final Validator validator;

    public BeanParameterProducer(Validator validator) {this.validator = validator;}

    @Override
    public P apply(Instance<Object> instance) {
        InjectionPoint injectionPoint = instance.select(InjectionPoint.class).get();

        return validator.validateBeanParameterInjectionPoint(injectionPoint);
    }

}
