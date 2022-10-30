package io.github.jhahnhro.enhancedcdi.util;

import javax.enterprise.context.Dependent;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collection;

/**
 * Helps with the lack of {@code select(Type, Annotation...)} in {@link javax.enterprise.inject.Instance}
 */
@Dependent
public class BeanHelper {
    @Inject
    BeanManager beanManager;

    public <T> Collection<BeanInstance<T>> select(final Type type, final Annotation... qualifiers) {
        //noinspection unchecked
        return beanManager.getBeans(type, qualifiers)
                .stream()
                .map(bean -> (Bean<T>) bean)
                .map(bean -> BeanInstance.createContextualReference(beanManager, bean, type))
                .toList();
    }
}