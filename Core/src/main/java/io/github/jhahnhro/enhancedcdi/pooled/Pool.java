package io.github.jhahnhro.enhancedcdi.pooled;

import java.util.function.Supplier;

public interface Pool<T> extends AutoCloseable {
    /**
     * Returns the current size of the pool. This method is not guaranteed to reflect the actual number of objects that
     * the pool can use without creating new ones. The actual number may be different under race conditions. Therefore,
     * the method should only be used for debugging purposes.
     *
     * @return the current size of the pool (more or less)
     */
    int size();

    /**
     * Borrows an item from the pool to execute the given action on it, returning the result.
     * <p>
     * The method creates a new item if all items in the pool are currently in use and the maximum size has not been
     * reached yet. If all items are in use and the maximum size has been reached, the method blocks until an item
     * becomes free to use or one gets destroyed (then it will getOrCreate a new item to use) or the thread gets
     * interrupted.
     * <p>
     * If an item needs to be created, but the {@link Supplier} returns {@code null}, a {@link NullPointerException}
     * will be thrown. If the supplier throws an exception, it will be rethrown.
     * <p>
     * If the action throws an exception, then the item that was passed to it will be destroyed and not returned to the
     * pool. The exception is then rethrown.
     * <p>
     * If the destroyer throws an exception, it will be rethrown (but the item still won't be returned to the pool).
     * <p>
     * The action MUST not let the item passed to it escape. Otherwise, non-predictable behaviour can occur. In
     * particular: Other calls to this method may re-use the item concurrently, or it may get destroyed concurrently at
     * any moment.
     *
     * @param action an action to perform with the item.
     * @param <V>    the type of the result of the action.
     * @param <EX>   exceptions the action is allows to throw
     * @return the result of the action on one of the items in this pool.
     * @throws IllegalStateException if the pool is already closed.
     * @throws NullPointerException  if a new item needed to be created, but the supplier returned {@code null}
     * @throws InterruptedException  if the current thread gets interrupted while waiting for a free item.
     */
    <V, EX extends Exception> V withItem(ThrowingFunction<T, V, EX> action) throws InterruptedException, EX;
    default <EX extends Exception> void withItem(ThrowingConsumer<T, EX> action) throws InterruptedException, EX{
        this.withItem(item -> {
            action.apply(item);
            return null;
        });
    }

    @Override
    void close();

    @FunctionalInterface
    interface ThrowingFunction<IN, OUT, EX extends Exception> {
        OUT apply(IN item) throws EX;
    }

    @FunctionalInterface
    interface ThrowingConsumer<IN, EX extends Exception> {
        void apply(IN item) throws EX;
    }
}
