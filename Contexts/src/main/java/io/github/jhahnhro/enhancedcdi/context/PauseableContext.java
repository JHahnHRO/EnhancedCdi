package io.github.jhahnhro.enhancedcdi.context;

import javax.enterprise.context.spi.AlterableContext;

/**
 * An {@link AlterableContext} that can be manually "paused", i.e. explicitly made inactive. The context may decide for
 * itself if and when it becomes active again, but an implementations may also support explicit (re)activation.
 * <p>
 * Pausing (and re-activation if supported) can happen multiple times.
 *
 * @apiNote <ul><li>Pausing is orthogonal to {@link javax.enterprise.inject.spi.PassivationCapable passivation}. A
 * passivation-capable scope like the {@link javax.enterprise.context.SessionScoped session scope} is still active while
 * its beans are passivated. A scope can be both pauseable and passivation-capable, either one or neither.<br />If it is
 * both, it may be a good idea to passivate the beans while the context is inactive, but that is not required.</li>
 * <li>No guarantees are made regarding thread-safety of pausing. An implementation may wait until it can be sure
 * that its contextual instances are no longer in use, but it does not have to.</li>
 * <li>Pausing may or may not destroy contextual instances. In other words: After pausing and re-activation the
 * contextual instances in this context may be the same as before or they may be completely new instances.</li>
 * </ul>
 */
public interface PauseableContext extends CloseableContext {

    /**
     * Optional. Activates this context (at least in the calling thread). Subsequent calls to {@link #isActive()} from
     * those threads will return {@code true} until {@link #pause()} is called.
     *
     * @return {@code true} if this context was previously inactive and is now active, {@code false} if it was already
     * active.
     * @throws ContextClosedException        if this context cannot be activated because it is already closed.
     * @throws IllegalStateException         if this context cannot be activated because a conflicting context is
     *                                       already active in the same thread. Implementations can choose to throw this
     *                                       exception to prevent having two contexts with the same scope active in the
     *                                       same thread.
     * @throws UnsupportedOperationException if manual activation is not supported by this context.
     */
    default boolean activate() {
        throw new UnsupportedOperationException("Context cannot be manually activated");
    }

    /**
     * Makes this context inactive in (at least) the current thread.
     *
     * @apiNote Must be idempotent.
     */
    void pause();
}
