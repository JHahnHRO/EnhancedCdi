package io.github.jhahnhro.enhancedcdi.pooled;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Provides a basic implementation of {@link #apply(ThrowingFunction)} using a semaphore to block.
 *
 * @param <T> type of the pooled objects
 */
public abstract class AbstractBlockingPool<T> implements BlockingPool<T> {
    /**
     * This pool's capacity.
     */
    private final int capacity;
    /**
     * A semaphore limiting the access to the pooled objects.
     */
    private final Semaphore permissionToUseItem;

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
        this.closed = new AtomicBoolean(false);
        this.closingFinished = new CountDownLatch(1);
    }

    @Override
    public int capacity() {
        return capacity;
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

    private T acquireItem() throws InterruptedException {
        if (closed.get()) {
            throw new IllegalStateException("BlockingPool closed.");
        }
        permissionToUseItem.acquire();
        return Objects.requireNonNull(borrowFromPool());
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
    protected abstract T borrowFromPool();

    private void releaseItem(T item, Exception ex) {
        if (ex == null) {
            returnToPool(item);
        } else {
            maybeReturnToPool(item, ex);
        }
        permissionToUseItem.release();
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
            try {
                // closed is now true and this thread has made the change. Now we block here until concurrent
                // executions that were still running when this method got called have finished.
                permissionToUseItem.acquireUninterruptibly(capacity);
                // After all executions have finished, we clean up.
                onClose();
            } finally {
                permissionToUseItem.release(capacity);
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
}
