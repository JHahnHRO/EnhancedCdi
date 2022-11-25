package io.github.jhahnhro.enhancedcdi.context;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A {@link PauseableContext} that starts inactive, allows manual {@link #activate() activation} and keeps track of all
 * threads in which it is active.
 * <p>
 * This context is also {@link SharedContext shared}, i.e. all threads this context which it is active see the same set
 * of contextual instances.
 *
 * @apiNote Thread-Safety: Calling {@link #close()} will deactivate the context in the calling thread if necessary, will
 * wait until all other threads become inactive too, then will all beans get destroyed.
 * <p>
 * Therefore, threads that activate themselves by calling {@link #activate()} MUST also call {@link #pause()}. Otherwise
 * {@link #close()} will just block forever.
 */
public abstract class ThreadAwarePauseableContext extends ForwardingContext implements SharedContext, PauseableContext {
    /**
     * Stores the contextual instances of this context.
     */
    private final GlobalContext beanStorage = new GlobalContext(null);
    private final Map<Thread, CountDownLatch> activeThreads = new WeakHashMap<>();
    /**
     * Guards against concurrent modification of {@link #activeThreads}, because WeakHashMap isn't thread-safe.
     */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();
    /**
     * Is this context closed?
     */
    private boolean closed = false;

    /**
     * @return {@code true} iff this context has been {@link #activate() activated} for the current thread.
     */
    @Override
    public boolean isActive() {
        readLock.lock();
        try {
            return !closed && activeThreads.containsKey(Thread.currentThread());
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public boolean activate() {
        writeLock.lock();
        try {
            checkOpen();
            return activeThreads.putIfAbsent(Thread.currentThread(), new CountDownLatch(1)) == null;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void pause() {
        writeLock.lock();
        try {
            final CountDownLatch latch = activeThreads.remove(Thread.currentThread());
            if (latch != null) {
                latch.countDown();
            }
        } finally {
            writeLock.unlock();
        }
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
        // Step 0: deactivate current thread if necessary.
        pause();

        List<CountDownLatch> latches;

        // Step 1: Lock to prevent anyone from activating this context in more threads than it is already active.
        writeLock.lock();
        try {
            // idempotent
            if (closed) {
                return;
            }
            closed = true;

            latches = new ArrayList<>(activeThreads.values());
            activeThreads.clear();
        } finally {
            writeLock.unlock();
        }

        // Step 2: Wait for all other threads to become inactive by calling #pause. This MUST happen outside the
        // write-lock, because #pause also needs the write-lock.
        for (CountDownLatch latch : latches) {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // Step 3: destroy all beans.
        beanStorage.close();
    }

    @Override
    public boolean isClosed() {
        readLock.lock();
        try {
            return closed;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    protected GlobalContext delegate() {
        readLock.lock();
        try {
            checkOpen();
            // delegate get(Contextual), get(Contextual, CreationalContext) and destroy(Contextual) to beanStorage
            checkActive();
            return beanStorage;
        } finally {
            readLock.unlock();
        }
    }
}
