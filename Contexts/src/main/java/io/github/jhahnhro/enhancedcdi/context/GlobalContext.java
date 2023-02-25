package io.github.jhahnhro.enhancedcdi.context;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import javax.enterprise.context.spi.Contextual;
import javax.enterprise.context.spi.CreationalContext;

import io.github.jhahnhro.enhancedcdi.util.BeanInstance;

/**
 * The simplest non-trivial {@link CloseableContext}. It is created active for all threads and stays active until it is
 * closed. It is {@link SharedContext shared} between all threads, i.e. all threads see the same set of contextual
 * instances.
 * <p>
 * It is backed by a {@link ConcurrentHashMap} and works basically the same as the context for the
 * {@link javax.inject.Singleton} scope until it is closed.
 *
 * @apiNote Closing is not thread-safe, i.e. client proxies that are still in use will throw
 * {@link javax.enterprise.context.ContextNotActiveException} when another thread concurrently calls {@link #close()}.
 */
public class GlobalContext implements SharedContext {
    /**
     * The backing map. {@code null} iff this context is closed.
     */
    private final AtomicReference<Map<Contextual<?>, BeanInstance<?>>> map = new AtomicReference<>(
            new ConcurrentHashMap<>());
    /**
     * This context's scope.
     */
    private final Class<? extends Annotation> scope;

    public GlobalContext(Class<? extends Annotation> scope) {
        this.scope = scope;
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return scope;
    }

    @Override
    public boolean isActive() {
        return !isClosed();
    }

    public <T> T get(Contextual<T> contextual, CreationalContext<T> context) {
        final BeanInstance<T> beanInstance = (BeanInstance<T>) getInstanceMap()//
                .computeIfAbsent(contextual, __ -> BeanInstance.createContextualInstance(contextual, context));
        return beanInstance.instance();
    }

    public <T> T get(Contextual<T> contextual) {
        final BeanInstance<T> beanInstance = (BeanInstance<T>) getInstanceMap().get(contextual);
        if (beanInstance != null) {
            return beanInstance.instance();
        }
        return null;
    }

    public void destroy(Contextual<?> contextual) {
        final BeanInstance<?> beanInstance = getInstanceMap().remove(contextual);
        if (beanInstance != null) {
            beanInstance.destroy();
        }
    }

    private Map<Contextual<?>, BeanInstance<?>> getInstanceMap() {
        final Map<Contextual<?>, BeanInstance<?>> instanceMap = map.get();
        if (instanceMap != null) {
            return instanceMap;
        }
        throw new ContextClosedException(this);
    }

    /**
     * Destroys all current contextual instances, but does not close the context.
     */
    public void destroyAll() {
        final Map<Contextual<?>, BeanInstance<?>> instanceMap = map.getAndUpdate(
                m -> m == null ? null : new ConcurrentHashMap<>());
        if (instanceMap != null) { // no-op if already closed
            instanceMap.values().forEach(BeanInstance::destroy);
        }
    }

    @Override
    public void close() {
        final Map<Contextual<?>, BeanInstance<?>> instanceMap = this.map.getAndSet(null);
        if (instanceMap != null) {
            instanceMap.values().forEach(BeanInstance::destroy);
        }
    }

    @Override
    public boolean isClosed() {
        return this.map.get() != null;
    }
}