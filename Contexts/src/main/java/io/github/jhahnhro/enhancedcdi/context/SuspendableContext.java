package io.github.jhahnhro.enhancedcdi.context;

import javax.enterprise.context.ContextNotActiveException;
import javax.enterprise.context.spi.AlterableContext;

/**
 * An {@link AlterableContext} that can be activated and deactivated. It starts inactive, can be activated and
 * deactivated multiple times. While a context is inactive, calling methods on beans its scope, will throw a
 * {@link javax.enterprise.context.ContextNotActiveException}.
 * <p>
 * Calling {@link #activate()} will make the context active will return {@code true}) in some set of threads (i.e.
 * subsequent calls from those threads to {@link #isActive()}, containing at least the calling thread and maybe others.
 * For example, in {@link GlobalSuspendableContext} a call to {@code activate()} will make the context active in all
 * threads. In {@link MultiThreadedSharedContext} a call to {@code activate()} will make only the calling thread
 * active.
 * <p>
 * {@code activate()} returns an {@link ActivationToken} whose {@link ActivationToken#close() close method} deactivates
 * the context again in the same set of threads.
 * <p>
 * No guarantees are made regarding thread-safety of deactivation. An implementation may wait until it can be sure that
 * its contextual instances are no longer in use, but it does not have to.
 *
 * @see GlobalSuspendableContext
 */
public interface SuspendableContext extends AlterableContext {
    /**
     * Convenience method that throws {@link ContextNotActiveException} if the context is not active in the current
     * thread.
     */
    default void checkActive() {
        if (!isActive()) {
            throw new ContextNotActiveException();
        }
    }

    /**
     * Activates this context some set of threads, including at least the current thread. Subsequent calls to
     * {@link #isActive()} from those threads will return {@code true} until {@link ActivationToken#close() close} is
     * called on the returned {@link ActivationToken}.
     * <p>
     * Multiple calls to this method before the call to {@link ActivationToken#close()} have no further effect and
     * return the same token as the first call.
     *
     * @return An {@link ActivationToken} whose {@link ActivationToken#close() close}-method deactivates this context
     * again in the same set of threads.
     * @throws ContextClosedException if this context is already closed.
     */
    ActivationToken activate();

    interface ActivationToken extends AutoCloseable {

        /**
         * @return the context that issued this token.
         * @see SuspendableContext#activate()
         */
        SuspendableContext issuingContext();

        /**
         * @return {@code true} iff {@link #close()} has not yet been called.
         */
        boolean isActive();

        /**
         * Deactivates the context that issued this token.
         *
         * @throws IllegalCallerException if this method is called from a thread other than ones that were originally
         *                                activated when this {@code ActivationToken} was created.
         * @apiNote Must be idempotent.
         */
        @Override
        void close();
    }
}
