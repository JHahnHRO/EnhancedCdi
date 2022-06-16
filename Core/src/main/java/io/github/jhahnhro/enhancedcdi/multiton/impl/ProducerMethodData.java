package io.github.jhahnhro.enhancedcdi.multiton.impl;

import org.eclipse.microprofile.config.Config;

import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanAttributes;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class ProducerMethodData<P, T, X extends Y, Y> extends BeanData<P, T> {
    private final Bean<X> declaringBean;
    private final AnnotatedMethod<Y> producerMethod;
    private final Map<P, AnnotatedMethod<Y>> resultingProducerMethods;

    public ProducerMethodData(Bean<X> declaringBean, AnnotatedMethod<Y> producerMethod,
                              BeanAttributes<T> beanAttributes, Class<P> parameterClass, String configProperty,
                              Config appConfig) {
        super(beanAttributes, parameterClass, configProperty, appConfig);
        this.declaringBean = declaringBean;
        this.producerMethod = producerMethod;
        this.resultingProducerMethods = new HashMap<>();
    }

    @Override
    public Type beanType() {
        return producerMethod.getBaseType();
    }

    public Bean<X> declaringBean() {
        return declaringBean;
    }

    public AnnotatedMethod<Y> producerMethod() {
        return producerMethod;
    }

    public Map<P, AnnotatedMethod<Y>> resultingProducerMethods() {
        return resultingProducerMethods;
    }
}
