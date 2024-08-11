package io.github.jhahnhro.enhancedcdi.context;

import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.context.spi.AlterableContext;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;

/**
 * An {@link AlterableContext} that can be {@link #close() closed}. Closing entails deactivating the context in all
 * threads in which it was previously active and destroying all contextual instances previously created. Once closed,
 * {@link #isActive()} will always return false and {@link #get(Contextual, CreationalContext)},
 * {@link #get(Contextual)}, as well as {@link #destroy(Contextual)} will throw {@link ContextClosedException}.
 * <p>
 * Closing is irreversible, as opposed to {@link jakarta.enterprise.inject.spi.PassivationCapable passivation} for
 * example.
 * <p>
 * No guarantees are made regarding thread-safety of closing. An implementation should make an effort to wait until it
 * can be sure that its contextual instances are no longer in use, before destroying them, but it does not have to. For
 * example {@link ThreadAwarePauseableContext} waits for all active threads to become inactive before destroying the
 * contextual instances.
 *
 * @see PauseableContext
 * @see SharedContext
 */
public interface CloseableContext extends AlterableContext, AutoCloseable {

    /**
     * Convenience method that throws {@link ContextNotActiveException} if this context is not active in the current
     * thread.
     */
    default void checkActive() {
        if (!isActive()) {
            throw new ContextNotActiveException();
        }
    }

    /**
     * Convenience method that throws {@link ContextClosedException} if this context is closed.
     */
    default void checkOpen() {
        if (isClosed()) {
            throw new ContextClosedException(this);
        }
    }

    /**
     * Deactivates this context permanently in all threads and destroys all beans in this context. Calls to
     * {@link #get(Contextual)}, {@link #get(Contextual, CreationalContext)} and {@link #destroy(Contextual)} will
     * always throw {@link jakarta.enterprise.context.ContextNotActiveException} afterwards, {@link #isActive()} will
     * always return {@code false}.
     *
     * @apiNote Must be idempotent, i.e. calling it multiple times has the same effect as calling it once.
     */
    @Override
    void close();

    /**
     * @return {@code true} iff this context is already closed.
     */
    boolean isClosed();
}
