package io.github.jhahnhro.enhancedcdi.context;

import javax.enterprise.context.spi.Contextual;
import javax.enterprise.context.spi.CreationalContext;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

class BeanStorage {
    private final AtomicReference<Map<Contextual<?>, ContextualInstance<?>>> map = new AtomicReference<>(
            new ConcurrentHashMap<>());

    <T> T get(Contextual<T> contextual, CreationalContext<T> context) {
        return (T) map.get()
                .computeIfAbsent(contextual, __ -> new ContextualInstance<>(contextual, context))
                .instance();
    }

    <T> T get(Contextual<T> contextual) {
        final ContextualInstance<T> contextualInstance = (ContextualInstance<T>) map.get().get(contextual);
        return contextualInstance == null ? null : contextualInstance.instance();
    }

    void destroy(Contextual<?> contextual) {
        final ContextualInstance<?> contextualInstance = map.get().remove(contextual);
        if (contextualInstance != null) {
            contextualInstance.destroy();
        }
    }

    void destroyAll() {
        final Map<Contextual<?>, ContextualInstance<?>> oldMap = map.getAndSet(new ConcurrentHashMap<>());
        oldMap.values().forEach(ContextualInstance::destroy);
    }

    private record ContextualInstance<T>(T instance, Contextual<T> contextual, CreationalContext<T> context) {
        public ContextualInstance(Contextual<T> contextual, CreationalContext<T> context) {
            this(contextual.create(context), contextual, context);
        }

        public void destroy() {
            contextual.destroy(instance, context);
        }
    }
}