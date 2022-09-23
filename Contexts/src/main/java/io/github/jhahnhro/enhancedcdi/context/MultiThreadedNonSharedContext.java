package io.github.jhahnhro.enhancedcdi.context;

import javax.enterprise.context.ContextNotActiveException;
import javax.enterprise.context.spi.Contextual;
import javax.enterprise.context.spi.CreationalContext;
import java.lang.annotation.Annotation;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A context that is multithreaded in the sense that multiple active threads can see the same set of contextual
 * instances, but not {@link SharedContext shared}, i.e. not ALL threads necessarily see the same set of contextual
 * instances.
 * <p>
 * An instance of this class manages "child"-contexts with the same {@link #getScope() scope}, each identified by a
 * unique key, e.g. a number or a {@link java.util.UUID}, and each with their own set of contextual instances
 * <p>
 * A child-context can be created from any thread by calling {@link #getOrCreate(KEY)}. Once created, it can be
 * {@link #activate(Object) activated} and {@link SuspendableContext.ActivationToken#close() deactivated} in any thread
 * as often as needed. Child-contexts are {@link CloseableSuspendableContext} and calling {@link #close(KEY)} will close
 * a specific child-context.
 * <p>
 * If this was part of the CDI standard, {@link javax.enterprise.context.SessionScoped} and
 * {@link javax.enterprise.context.ConversationScoped} would be backed by something similar. In contrast to
 * {@link javax.enterprise.context.RequestScoped} which is usually single-threaded, i.e. every thread in which a
 * RequestContext is active sees its own set of contextual instances.
 *
 * @param <KEY> the type of key identifying the child-contexts.
 */
// TODO: race condition close vs. getOrCreate => replace ConcurrentHashMap by HashMap+RW-Lock
public abstract class MultiThreadedNonSharedContext<KEY> implements CloseableContext {

    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Invariant: activationToken.get() != null => contexts contains a ChildContext that issued the token
     */
    private final ThreadLocal<SuspendableContext.ActivationToken> activationToken = new ThreadLocal<>();
    private final ConcurrentMap<KEY, ChildContext> contexts = new ConcurrentHashMap<>();

    @Override
    public boolean isActive() {
        final SuspendableContext.ActivationToken token = activationToken.get();
        return token != null && token.isActive();
    }

    public CloseableSuspendableContext getOrCreate(KEY key) {
        throwIfClosed();
        return contexts.computeIfAbsent(key, ChildContext::new);
    }

    public SuspendableContext.ActivationToken activate(KEY key) {
        throwIfClosed();
        final ChildContext childContext = contexts.get(key);
        if (childContext != null) {
            return childContext.activate();
        } else {
            throw new IllegalArgumentException(
                    "Unknown context key " + key + ". Make sure to call getOrCreate(KEY) first.");
        }
    }

    private void throwIfClosed() {
        if (closed.get()) {
            throw new ContextClosedException(this);
        }
    }

    public void close() {
        if (closed.compareAndSet(false, true)) {
            contexts.values().forEach(CloseableContext::close);
            contexts.clear();
        }
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    public void close(KEY key) {
        final ChildContext childContext = contexts.remove(key);
        if (childContext != null) {
            childContext.close();
        }
    }

    @Override
    public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
        return getChildContextOrThrow().get(contextual, creationalContext);
    }

    @Override
    public <T> T get(Contextual<T> contextual) {
        return getChildContextOrThrow().get(contextual);
    }

    @Override
    public void destroy(Contextual<?> contextual) {
        getChildContextOrThrow().destroy(contextual);
    }

    private ChildContext getChildContextOrThrow() {
        throwIfClosed();
        final SuspendableContext.ActivationToken token = activationToken.get();
        if (token != null) {
            return (ChildContext) token.issuingContext();
        }
        throw new ContextNotActiveException();
    }

    private final class ChildContext extends MultiThreadedSharedContext {
        private final KEY key;

        ChildContext(KEY key) {
            super(MultiThreadedNonSharedContext.this.activationToken);
            this.key = key;
        }

        @Override
        public Class<? extends Annotation> getScope() {
            return MultiThreadedNonSharedContext.this.getScope();
        }
    }
}
