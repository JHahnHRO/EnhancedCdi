package io.github.jhahnhro.enhancedcdi.multiton.impl;

import org.eclipse.microprofile.config.Config;

import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.BeanAttributes;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public final class BeanClassData<P, T> extends BeanData<P, T> {
    private final AnnotatedType<T> annotatedType;
    private final Map<P, AnnotatedType<T>> resultingTypes;

    public BeanClassData(AnnotatedType<T> annotatedType, BeanAttributes<T> beanAttributes, Class<P> parameterClass,
                         String configProperty, Config appConfig) {
        super(beanAttributes, parameterClass, configProperty, appConfig);
        this.annotatedType = annotatedType;
        this.resultingTypes = new HashMap<>();
    }

    @Override
    public Type beanType() {
        return annotatedType.getBaseType();
    }

    public AnnotatedType<T> annotatedType() {
        return annotatedType;
    }

    public Map<P, AnnotatedType<T>> resultingTypes() {
        return resultingTypes;
    }
}
