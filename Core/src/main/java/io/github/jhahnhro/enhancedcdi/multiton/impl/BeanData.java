package io.github.jhahnhro.enhancedcdi.multiton.impl;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.Converter;

import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanAttributes;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class BeanData<P, T> {
    private final BeanAttributes<T> beanAttributes;
    private final Map<P, Bean<T>> resultingBeans;
    private final Class<P> parameterClass;
    private final String configProperty;
    private final Map<String, P> parameters;

    BeanData(BeanAttributes<T> beanAttributes, Class<P> parameterClass, String configProperty, Config appConfig) {
        this.parameterClass = parameterClass;
        this.resultingBeans = new HashMap<>();
        this.beanAttributes = beanAttributes;
        this.configProperty = configProperty;

        if (configProperty == null) {
            // only happens if the parameterClass is an enum class. Then we assume that all enum constants are
            // meant to be used as parameters.
            this.parameters = Arrays.stream(parameterClass.getEnumConstants())
                    .collect(Collectors.toUnmodifiableMap(p -> ((Enum<?>) p).name(), Function.identity()));
        } else {
            @SuppressWarnings("OptionalGetWithoutIsPresent") // was validated before
            Converter<P> converter = appConfig.getConverter(parameterClass).get();

            this.parameters = Arrays.stream(appConfig.getValue(configProperty, String[].class))
                    .collect(Collectors.toUnmodifiableMap(Function.identity(), converter::convert));
        }

    }

    public BeanAttributes<T> beanAttributes() {
        return beanAttributes;
    }

    public abstract Type beanType();

    public Map<P, Bean<T>> resultingBeans() {
        return resultingBeans;
    }

    public Class<P> parameterClass() {
        return parameterClass;
    }

    public String configProperty() {
        return configProperty;
    }

    public Map<String, P> parameters() {
        return parameters;
    }
}
