package io.github.jhahnhro.enhancedcdi.context;

import javax.enterprise.context.spi.Contextual;
import javax.enterprise.context.spi.CreationalContext;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@link SuspendableContext suspendable} and {@link SharedContext shared} that is either active in all threads or not
 * active in all threads.
 * <p>
 * Thread-safety: {@link #close() Closing} the context is not guaranteed to be synchronized in any way, i.e. if some
 * thread still uses some client proxies of this context, it may suddenly find itself confronted with a
 * {@link javax.enterprise.context.ContextNotActiveException} when another thread concurrently calls {@code close()}.
 */
public abstract class GlobalSuspendableContext implements SuspendableContext, CloseableContext, SharedContext {
    private final BeanStorage beanStorage = new BeanStorage();

    /**
     * Used both for closing and activation. token==null means this context is closed.
     */
    private volatile InternalToken token = new InternalToken();


    @Override
    public boolean isActive() {
        final InternalToken token = this.token;
        return token != null && token.active.get();
    }

    @Override
    public ActivationToken activate() {
        final InternalToken token = this.token;
        if (token != null) {
            token.active.set(true);
            return token;
        } else {
            throw new IllegalStateException("Context already closed.");
        }
    }

    @Override
    public void close() {
        this.token = null;
        // TODO fix race condition with #get
        this.beanStorage.destroyAll();
    }

    @Override
    public void destroy(Contextual<?> contextual) {
        checkActive();
        beanStorage.destroy(contextual);
    }

    @Override
    public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
        checkActive();
        return beanStorage.get(contextual, creationalContext);
    }

    @Override
    public <T> T get(Contextual<T> contextual) {
        checkActive();
        return beanStorage.get(contextual);
    }

    private class InternalToken implements ActivationToken {
        private final AtomicBoolean active = new AtomicBoolean(false);

        @Override
        public SuspendableContext issuingContext() {
            return GlobalSuspendableContext.this;
        }

        @Override
        public boolean isActive() {
            return active.get();
        }

        @Override
        public void close() {
            active.set(false);
        }
    }
}
