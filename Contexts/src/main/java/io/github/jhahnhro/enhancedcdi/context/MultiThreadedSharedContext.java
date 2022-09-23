package io.github.jhahnhro.enhancedcdi.context;

import javax.enterprise.context.spi.Contextual;
import javax.enterprise.context.spi.CreationalContext;
import java.util.concurrent.Phaser;

/**
 * A multithreaded and {@link SharedContext shared} context, i.e. all threads in which it is active see the same set of
 * contextual instances. It is {@link SuspendableContext suspendable} and active precisely in the threads that called
 * {@link #activate()} themselves, i.e. {@code activate()} activates the context only in the calling thread (until
 * either {@link ActivationToken#close()} or {@link #deactivate()} is called).
 * <p>
 * Furthermore, the context is {@link CloseableContext closable}. Once it is closed, the context can never be activated
 * again.
 * <p>
 * Thread-Safety: Calling {@link #close()} will deactivate the context in the calling thread if it was active at the
 * time, will wait until all other threads become inactive too, and only then will all beans get destroyed. Thus, client
 * proxies that are still in use will not unexpectedly throw {@link javax.enterprise.context.ContextNotActiveException}
 * when another thread concurrently calls {@code close()}.
 */
public abstract class MultiThreadedSharedContext implements CloseableSuspendableContext, SharedContext {

    /**
     * A {@link Phaser} with which threads register themselves on {@link #activate() activation} and de-register on
     * {@link #deactivate() deactivation} that is used to wait for active threads before destroying this context.
     * <p>
     * Invariants:
     * <ul>
     *     <li>There is at least one party registered with the phaser iff this context is alive. In particular: There
     *     is only one phase and the phaser terminates when this contexts is closed.</li>
     *     <li>During this context's lifetime, the number of registered parties equals 1 + number of threads in which
     *     this context is active.</li>
     * </ul>
     */
    protected final Phaser activationPhaser;
    /**
     * Stores the contextual instances of this context.
     */
    private final BeanStorage beanStorage = new BeanStorage();
    private final ThreadLocal<ActivationToken> activationToken;

    protected MultiThreadedSharedContext() {
        this(new ThreadLocal<>());
    }

    /**
     * Constructs a new context instance with a ThreadLocal of {@link ActivationToken}s that defines if the current
     * thread is active. If some other context except this one has put a value in that {@code ThreadLocal}, this context
     * cannot be activated. This can be used to ensure that among multiple contexts with the same
     * {@link #getScope() scope} only at most is active per thread.
     *
     * @param activationToken a ThreadLocal with {@link ActivationToken}s that defines if the current thread is active.
     */
    protected MultiThreadedSharedContext(final ThreadLocal<ActivationToken> activationToken) {
        this.activationToken = activationToken;
        this.activationPhaser = new Phaser();

        // register once to mark this context as alive. Corresponding call to deregister is in #close
        this.activationPhaser.register();
    }

    /**
     * @return {@code true} iff this context has been {@link #activate() activated} for the current thread.
     */
    @Override
    public boolean isActive() {
        final ActivationToken token = activationToken.get();
        return issuedByThisContext(token) && token.isActive();
    }

    /**
     * Activates this context in the current thread. Subsequent calls to {@link #isActive()} from the same thread will
     * return {@code true} until {@link ActivationToken#close()} or {@link #deactivate()} is called. Activation of other
     * threads is unaffected.
     * <p>
     * Multiple calls to this method before the next call to {@link #deactivate()} have no further effect and return the
     * same object as the first call.
     * <p>
     * ATTENTION! This method has synchronization side effects. A call to {@link #close() this.close()} will wait for
     * all threads that have previously activated this context to deactivate it. Therefore, you MUST ensure that
     * {@link #deactivate()} (or {@link ActivationToken#close()} on the returned token) is called at some later point,
     * otherwise this context will never be closed and the contextual instances it contains may never be destroyed.
     *
     * @return An {@link ActivationToken} whose {@link ActivationToken#close() close}-Method deactivates this context in
     * the current thread again so that this method can be used with a try-with-resources block.
     * @throws ContextClosedException if this context is already closed
     * @throws IllegalStateException  if another context of the same scope has been detected to be already active in the
     *                                current thread
     */
    @Override
    public ActivationToken activate() {
        ActivationToken token = activationToken.get();
        if (issuedByThisContext(token)) {
            ((InternalToken) token).active = true; // re-activate if necessary
            return token;
        } else if (token != null) {
            // token issued by a different context => throw
            throw new IllegalStateException("Context cannot be activated, "
                                            + "because a conflicting context is already active in the current thread");
        }
        token = new InternalToken(); // may throw the ContextClosedException
        activationToken.set(token);
        return token;
    }

    private boolean issuedByThisContext(ActivationToken token) {
        return token instanceof InternalToken && token.issuingContext() == this;
    }

    /**
     * Deactivates this context in the current thread if it is already active, throw
     * {@link javax.enterprise.context.ContextNotActiveException} otherwise. Equivalent to calling
     * {@link ActivationToken#close()} on the token returned by an earlier call to {@link #activate()}.
     *
     * @throws javax.enterprise.context.ContextNotActiveException if this context is not currently active in the calling
     *                                                            thread.
     */
    public void deactivate() {
        checkActive();
        activationToken.get().close();
    }

    /**
     * Deactivates this context in this thread if it was active, blocks until all other threads have deactivated this
     * context, then destroys all contextual instances.
     * <p>
     * This context cannot be activated again afterwards.
     * <p>
     * Note that during the wait for others threads to become inactive, more threads can call {@link #activate()}. It is
     * possible that this will starve the current thread.
     */
    @Override
    public void close() {
        final ActivationToken token = activationToken.get();
        if (this.isActive()) {
            token.close();
        }
        final int phase = activationPhaser.awaitAdvance(activationPhaser.arriveAndDeregister());

        // at this point, the phaser is terminated and calling register on it will do nothing,
        // i.e. this.activate() will throw IllegalStateException from now on.

        if (phase >= 0) { // idempotent
            beanStorage.destroyAll();
        }
    }

    @Override
    public boolean isClosed() {
        return activationPhaser.getPhase() < 0;
    }

    @Override
    public <T> T get(Contextual<T> contextual, CreationalContext<T> context) {
        checkActive();
        return beanStorage.get(contextual, context);
    }

    @Override
    public <T> T get(Contextual<T> contextual) {
        checkActive();
        return beanStorage.get(contextual);
    }

    @Override
    public void destroy(Contextual<?> contextual) {
        checkActive();
        beanStorage.destroy(contextual);
    }


    private final class InternalToken implements ActivationToken {
        private boolean active = true;

        InternalToken() {
            final int phase = activationPhaser.register();
            if (phase < 0) {
                // phaser terminated => context closed
                throw new ContextClosedException(MultiThreadedSharedContext.this);
            }
        }

        @Override
        public SuspendableContext issuingContext() {
            return MultiThreadedSharedContext.this;
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public void close() {
            if (this != activationToken.get()) {
                throw new IllegalCallerException();
            }

            if (active) { // idempotent
                active = false;
                activationPhaser.arriveAndDeregister();
            }
        }
    }
}
