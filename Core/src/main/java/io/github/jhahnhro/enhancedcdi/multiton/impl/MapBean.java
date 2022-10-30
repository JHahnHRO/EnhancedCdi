package io.github.jhahnhro.enhancedcdi.multiton.impl;

import io.github.jhahnhro.enhancedcdi.types.ParameterizedTypeImpl;

import javax.enterprise.context.Dependent;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanAttributes;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.InjectionPoint;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class MapBean<P, T> implements Bean<Map<P, T>> {
    private final BeanAttributes<T> beanAttributes;
    private final Map<P, Bean<T>> underlyingBeans;
    private final Set<Type> mapTypes;

    private final BeanManager beanManager;

    // used to store CreationalContexts for the individual beans
    private final Map<CreationalContext<Map<P, T>>, Map<P, CreationalContext<T>>> creationalContexts;

    public MapBean(BeanData<P, T> beanData, BeanManager beanManager) {

        final Class<P> parameterClass = beanData.parameterClass();
        this.mapTypes = beanData.beanAttributes()
                .getTypes()
                .stream()
                .map(type -> new ParameterizedTypeImpl(Map.class, null, parameterClass, type))
                .collect(Collectors.toUnmodifiableSet());

        this.beanManager = beanManager;

        this.creationalContexts = new ConcurrentHashMap<>();
        this.beanAttributes = beanData.beanAttributes();
        this.underlyingBeans = beanData.resultingBeans();
    }

    @Override
    public Set<Type> getTypes() {
        return mapTypes;
    }

    @Override
    public Set<Annotation> getQualifiers() {
        return beanAttributes.getQualifiers();
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return Dependent.class;
    }

    @Override
    public String getName() {
        // no EL name
        return null;
    }

    @Override
    public Set<Class<? extends Annotation>> getStereotypes() {
        return beanAttributes.getStereotypes();
    }

    @Override
    public boolean isAlternative() {
        return beanAttributes.isAlternative();
    }

    @Override
    public Class<?> getBeanClass() {
        return MapBean.class;
    }

    @Override
    public Set<InjectionPoint> getInjectionPoints() {
        return Collections.emptySet();
    }

    @Override
    public boolean isNullable() {
        return false;
    }

    @Override
    public Map<P, T> create(CreationalContext<Map<P, T>> parentContext) {

        Map<P, T> result = new HashMap<>();
        Map<P, T> unmodifiableResult = Collections.unmodifiableMap(result);
        parentContext.push(unmodifiableResult);

        underlyingBeans.forEach((parameter, bean) -> {
            CreationalContext<T> childContext = beanManager.createCreationalContext(bean);
            creationalContexts.computeIfAbsent(parentContext, __ -> new HashMap<>()).put(parameter, childContext);

            T contextualReference = bean.create(childContext);
            result.put(parameter, contextualReference);
        });

        return unmodifiableResult;
    }


    @Override
    public void destroy(Map<P, T> mapInstance, CreationalContext<Map<P, T>> parentContext) {
        try {
            final Map<P, CreationalContext<T>> childContexts = this.creationalContexts.remove(parentContext);
            if (childContexts != null) {
                childContexts.forEach((parameter, childContext) -> {
                    final Bean<T> bean = underlyingBeans.get(parameter);
                    final T instance = mapInstance.get(parameter);
                    try {
                        bean.destroy(instance, childContext);
                    } finally {
                        childContext.release();
                    }
                });
            }
        } finally {
            parentContext.release();
        }
    }
}
