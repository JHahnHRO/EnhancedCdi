package io.github.jhahnhro.enhancedcdi.pooled;

/**
 * A pool of reusable objects. An object can be borrowed from the pool to
 * {@link #apply(ThrowingFunction) perform some action} with it. The pool guarantees that other threads will not perform
 * actions on the same item concurrently. If all items are currently in use, {@link #apply(ThrowingFunction)} blocks the
 * calling thread until an item becomes available.
 *
 * @param <T> type of the pooled objects
 */
public interface BlockingPool<T> extends AutoCloseable {
    /**
     * Returns the current size of the pool. Always less or equal than {@link #capacity()}.
     *
     * @return the current size of the pool.
     */
    int size();

    /**
     * @return The capacity of this pool.
     */
    int capacity();

    /**
     * Borrows an item from the pool to execute the given action on it, returning it to the pool after completion if
     * possible. The value returned by the action is then returned.
     * <p>
     * The pool guarantees that another thread will not perform actions on the same item concurrently (but it may on
     * other items in the pool). If all items are currently in use, this method blocks the calling thread until an item
     * becomes available or the thread gets interrupted.
     * <p>
     * If the action throws an exception, then the item that was passed to it may or may not be returned to the pool
     * depending on the exception. In any case, the exception is re-thrown to the caller.
     *
     * @param action an action to perform with the item.
     * @param <V>    the type of the result of the action.
     * @param <EX>   exceptions the action is allowed to throw
     * @return the result of the action on one of the items in this pool.
     * @throws IllegalStateException if the pool is already closed.
     * @throws NullPointerException  if {@code action} is {@code null}.
     * @throws InterruptedException  if the current thread gets interrupted while waiting for a free item.
     * @apiNote The action MUST not let the item passed to it escape. Otherwise, non-predictable behaviour can occur. In
     * particular: Other calls to this method may re-use the item concurrently, or it may get destroyed concurrently at
     * any moment.
     */
    <V, EX extends Exception> V apply(ThrowingFunction<T, V, EX> action) throws InterruptedException, EX;

    /**
     * Borrows an item from the pool to execute an action without return value on it, returning it to the pool after
     * completion if possible.
     *
     * @see #apply(ThrowingFunction)
     */
    default <EX extends Exception> void run(ThrowingConsumer<T, EX> action) throws InterruptedException, EX {
        this.apply(item -> {
            action.accept(item);
            return null;
        });
    }

    /**
     * Closes this pool. This will cause any further calls to {@link #apply(ThrowingFunction)} and
     * {@link #run(ThrowingConsumer)} to throw an {@link IllegalStateException}. The method blocks until all calls to
     * {@code apply} / {@code run} have finished that were in progress when this method got called.
     * <p>
     * All resources associated with this pool will be cleaned up.
     * <p>
     * This method is idempotent.
     */
    @Override
    void close();

    @FunctionalInterface
    interface ThrowingFunction<IN, OUT, EX extends Exception> {
        OUT apply(IN item) throws EX;
    }

    @FunctionalInterface
    interface ThrowingConsumer<IN, EX extends Exception> {
        void accept(IN item) throws EX;
    }
}
