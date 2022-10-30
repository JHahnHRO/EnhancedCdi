package io.github.jhahnhro.enhancedcdi.context;

import io.github.jhahnhro.enhancedcdi.util.BeanInstance;

import javax.enterprise.context.spi.Contextual;
import javax.enterprise.context.spi.CreationalContext;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

class BeanStorage {
    private final AtomicReference<Map<Contextual<?>, BeanInstance<?>>> map = new AtomicReference<>(
            new ConcurrentHashMap<>());

    <T> T get(Contextual<T> contextual, CreationalContext<T> context) {
        return (T) map.get()
                .computeIfAbsent(contextual, __ -> BeanInstance.createContextualInstance(contextual, context))
                .instance();
    }

    <T> T get(Contextual<T> contextual) {
        final BeanInstance<T> beanInstance = (BeanInstance<T>) map.get().get(contextual);
        return beanInstance == null ? null : beanInstance.instance();
    }

    void destroy(Contextual<?> contextual) {
        final BeanInstance<?> beanInstance = map.get().remove(contextual);
        if (beanInstance != null) {
            beanInstance.destroy();
        }
    }

    void destroyAll() {
        final Map<Contextual<?>, BeanInstance<?>> oldMap = map.getAndSet(new ConcurrentHashMap<>());
        oldMap.values().forEach(BeanInstance::destroy);
    }

}