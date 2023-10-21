package io.github.jhahnhro.enhancedcdi.pooled;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * Provides a basic implementation of {@link #apply(ThrowingFunction)} using a semaphore to block.
 *
 * @param <T> type of the pooled objects
 */
public abstract class AbstractBlockingPool<T> implements BlockingPool<T> {
    private final Lock poolLock;
    /**
     * A semaphore limiting the access to the pooled objects.
     */
    private final Semaphore permissionToUseItem;
    /**
     * This pool's capacity.
     */
    private volatile int capacity;

    /**
     * whether this pool is closed.
     */
    private final AtomicBoolean closed;
    private final CountDownLatch closingFinished;

    /**
     * Constructs a new instance with the given capacity.
     *
     * @param capacity the capacity this pool will have. Must be non-negative.
     * @throws IllegalArgumentException if {@code capacity<0}
     */
    protected AbstractBlockingPool(int capacity) {
        this.capacity = capacity;
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must not be negative");
        }
        this.permissionToUseItem = new Semaphore(capacity, true);
        this.poolLock = new InternalLock();
        this.closed = new AtomicBoolean(false);
        this.closingFinished = new CountDownLatch(1);
    }

    @Override
    public final int capacity() {
        return capacity;
    }

    /**
     * Returns a {@link Lock} that locks the whole pool, i.e. its lock-methods acquire all items in the pool and its
     * unlock method releases all items, so that actions performed under this lock are guaranteed that no calls to
     * {@link #apply(ThrowingFunction)} or {@link #run(ThrowingConsumer)} are concurrently executing.
     * <p>
     * The returned lock is reentrant but does NOT support {@link Lock#newCondition()}.
     *
     * @return a {@link Lock} that locks the whole pool.
     */
    protected final Lock getLock() {
        return poolLock;
    }

    /**
     * {@link #borrowFromPool() Borrows an item} from the pool to execute the given action on it,
     * {@link #returnToPool(Object) returning it} to the pool after completion if possible. The value returned by the
     * action is then returned.
     * <p>
     * The pool guarantees that another thread will not perform actions on the same item concurrently (but it may on
     * other items in the pool). If all items are currently in use, this method blocks the calling thread until an item
     * becomes available or the thread gets interrupted.
     * <p>
     * If the action throws an exception, then the item and the exception are passed to
     * {@link #maybeReturnToPool(Object, Exception)} which decided whether the item can be re-used. In any case, the
     * exception is re-thrown to the caller.
     *
     * @param action an action to perform with the item.
     * @param <V>    the type of the result of the action.
     * @param <EX>   exceptions the action is allowed to throw
     * @return the result of the action on one of the items in this pool.
     * @throws IllegalStateException if this pool is already closed.
     * @throws NullPointerException  if {@code action} is {@code null} or if {@link #borrowFromPool()} returned
     *                               {@code null}.
     * @throws InterruptedException  if the current thread gets interrupted while waiting for an item to become
     *                               available.
     * @apiNote The action MUST NOT let the item passed to it escape. Otherwise, non-predictable behaviour can occur. In
     * particular: Other calls to this method may re-use the item concurrently, or it may get destroyed concurrently at
     * any moment.
     */
    @Override
    public final <V, EX extends Exception> V apply(ThrowingFunction<T, V, EX> action) throws InterruptedException, EX {
        T item = acquireItem();
        Exception exception = null;
        try {
            return action.apply(item);
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            releaseItem(item, exception);
        }
    }

    @Override
    public final <EX extends Exception> void run(ThrowingConsumer<T, EX> action) throws InterruptedException, EX {
        BlockingPool.super.run(action);
    }

    private T acquireItem() throws InterruptedException {
        permissionToUseItem.acquire();
        if (closed.get()) {
            permissionToUseItem.release();
            throw new IllegalStateException("BlockingPool closed.");
        }
        try {
            return Objects.requireNonNull(borrowFromPool());
        } catch (InterruptedException | RuntimeException e) {
            permissionToUseItem.release();
            throw e;
        }
    }

    /**
     * Called when {@link #apply(ThrowingFunction)} obtains the item from the pool which will be passed to the action.
     * <p>
     * Items may not necessarily exist before this method is called, i.e. an implementation is free to create items on
     * demand.
     *
     * @return An item from the pool. Must not be null.
     * @implSpec Before {@link #returnToPool(Object) returnToPool} is called with the same instance, the method must not
     * return that instance again.
     */
    protected abstract T borrowFromPool() throws InterruptedException;

    private void releaseItem(T item, Exception ex) {
        try {
            if (ex == null) {
                returnToPool(item);
            } else {
                maybeReturnToPool(item, ex);
            }
        } finally {
            permissionToUseItem.release();
        }
    }

    /**
     * Called when {@link #apply(ThrowingFunction)} has successfully executed the given action and is done with the
     * item. The implementation should return the item back into the pool and make it available to be borrowed again if
     * possible.
     *
     * @param item the item. Not null.
     */
    protected abstract void returnToPool(T item);

    /**
     * Called when {@link #apply(ThrowingFunction)} has failed to execute the given action and is done with the item.
     * The implementation should return the item back into the pool and make it available to be borrowed again if
     * possible.
     *
     * @param item the item. Not null.
     * @param ex   the exception that was thrown by the action.
     * @implNote The default implementation simply calls {@link #returnToPool(Object)}, ignoring the exception.
     */
    protected void maybeReturnToPool(T item, Exception ex) {
        returnToPool(item);
    }

    @Override
    public final void close() {
        if (closed.compareAndSet(false, true)) {
            // closed is now true and this thread has made the change.
            poolLock.lock();
            try {
                onClose();
            } finally {
                poolLock.unlock();
                closingFinished.countDown();
            }
        } else {
            // closed is already true and some other thread has called the clean-up method. Now we block here until
            // concurrent executions that were still running when this method got called have finished.
            try {
                closingFinished.await();
            } catch (InterruptedException e) {
                // we're already closing down. Not much else to do.
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Called when this pool is {@link #close() closed}. An implementation should perform all necessary clean-up.
     * <p>
     * This method will be called exactly once by the first thread calling {@link #close()}, but not on subsequent calls
     * to {@code close()}. It is guaranteed that no items are still in use when it is called.
     */
    protected void onClose() {

    }


    /**
     * An implementation for {@link BlockingPool.ResizeMixin} for use in subclasses that want to implement the resize
     * capability.
     *
     * @implNote Contains a short-cut for the case that {@code capacity == newCapacity} in which case nothing is done
     * and no blocking happens.
     */
    protected abstract class ResizeMixin implements BlockingPool.ResizeMixin {

        @Override
        public final void resize(int newCapacity) {
            if (newCapacity <= 0) {
                throw new IllegalArgumentException("capacity must be positive.");
            }
            if (capacity == newCapacity) {
                return;
            }

            // setting capacity will make #runExclusive release the new amount of permits
            poolLock.lock();
            try {
                onResize(newCapacity);
                // setting capacity will make InternalLock#unlock release the right amount of permits
                capacity = newCapacity;
            } finally {
                poolLock.unlock();
            }
        }

        /**
         * The action needed to resize the pool. Guaranteed to be executed under the pool's
         * {@link #getLock() full lock}.
         *
         * @param newCapacity new desired new capacity
         */
        protected abstract void onResize(int newCapacity);
    }

    private class InternalLock implements Lock {

        /**
         * The owner of this lock. Will only be updated by the thread that holds all the permits.
         */
        @SuppressWarnings("java:S3077") // Sonar warns that just using "volatile" may not be enough for thread-safety
        private volatile Thread owner = null;
        /**
         * Invariants: {@code holdCount == 0} iff {@code owner == null} and {@code holdCount > 0} iff
         * {@code owner != null}
         * <p>
         * Only the owning thread ever updates this value while having all the permits.
         */
        private int holdCount = 0;

        @Override
        public void lock() {
            if (this.owner == Thread.currentThread()) {
                lockedAgain();
            } else {
                permissionToUseItem.acquireUninterruptibly(capacity);
                newlyLocked();
            }
        }

        @Override
        public void lockInterruptibly() throws InterruptedException {
            if (this.owner == Thread.currentThread()) {
                lockedAgain();
            } else {
                permissionToUseItem.acquire(capacity);
                newlyLocked();
            }
        }

        @Override
        public boolean tryLock() {
            if (this.owner == Thread.currentThread()) {
                lockedAgain();
                return true;
            } else {
                final boolean acquired = permissionToUseItem.tryAcquire(capacity);
                if (acquired) {
                    newlyLocked();
                }
                return acquired;
            }
        }

        @Override
        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            if (this.owner == Thread.currentThread()) {
                lockedAgain();
                return true;
            } else {
                final boolean acquired = permissionToUseItem.tryAcquire(capacity, time, unit);
                if (acquired) {
                    newlyLocked();
                }
                return acquired;
            }
        }

        @Override
        public void unlock() {
            if (this.owner == Thread.currentThread()) {
                this.holdCount--;
                if (this.holdCount == 0) {
                    this.owner = null;
                    permissionToUseItem.release(capacity);
                }
            } else {
                throw new IllegalMonitorStateException();
            }
        }

        private void lockedAgain() {
            this.holdCount++;
        }

        private void newlyLocked() {
            this.owner = Thread.currentThread();
            this.holdCount = 1;
        }

        @Override
        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }
    }
}
