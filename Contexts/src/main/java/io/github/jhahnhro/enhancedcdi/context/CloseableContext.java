package io.github.jhahnhro.enhancedcdi.context;

import javax.enterprise.context.spi.AlterableContext;

/**
 * An {@link AlterableContext} that can be {@link #close() closed}. Closing entails deactivating the context in all
 * threads in which it was previously active and destroying all contextual instances. Once closed, {@link #isActive()}
 * will always return false. Closing is irreversible.
 * <p>
 * No guarantees are made regarding thread-safety of closing. An implementation may wait until it can be sure that its
 * contextual instances are no longer in use, before destroying them, but it does not have to.
 */
public interface CloseableContext extends AlterableContext, AutoCloseable {

    /**
     * Deactivates this context permanently in all threads and destroys all beans in this context. This context cannot
     * be activated again afterwards.
     * <p>
     *
     * @apiNote Must be idempotent, i.e. calling it multiple times has the same effect as calling it once.
     */
    @Override
    void close();
}
