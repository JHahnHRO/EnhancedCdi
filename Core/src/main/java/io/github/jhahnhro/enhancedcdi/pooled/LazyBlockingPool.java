package io.github.jhahnhro.enhancedcdi.pooled;

import io.github.jhahnhro.enhancedcdi.util.Cleaning;

import java.lang.System.Logger.Level;
import java.lang.ref.Cleaner;
import java.util.Collection;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * An abstract implementation of {@link BlockingPool} that creates items lazily with {@link #create()} if the capacity
 * of this pool has not yet been reached. Optionally, a {@link Consumer} can be specified at construction that is used
 * to automatically destroy items if an action fails due to an exception and not returned to the pool.
 *
 * @param <T> type of pooled objects
 */
public abstract class LazyBlockingPool<T> implements BlockingPool<T> {

    private static final System.Logger LOG = System.getLogger(LazyBlockingPool.class.getCanonicalName());

    /**
     * Used to destroy items when the pool gets closed or an action passed to {@link #withItem(ThrowingFunction)} threw
     * an exception.
     */
    protected final Consumer<? super T> destroyer;

    /**
     * The items that are not currently in use inside {@link #withItem(ThrowingFunction)}
     */
    protected final Queue<T> itemsNotInUse;
    /**
     * A semaphore with {@link #capacity} permits.
     */
    private final Semaphore permissionToUseItem;

    private final int capacity;
    private final AtomicInteger size;
    /**
     * Whether this pool is closed.
     */
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Action to execute when this pool gets closed or garbage collected.
     */
    private final Cleaner.Cleanable cleanable;

    /**
     * Creates a new instance.
     *
     * @param initialSize number of items to create initially
     * @param capacity    maximum number of items in the pool
     */
    public LazyBlockingPool(int initialSize, int capacity) {
        this(initialSize, capacity, item -> {});
    }

    /**
     * Creates a new instance.
     *
     * @param initialSize number of items to create initially
     * @param capacity    maximum number of items in the pool
     * @param destroyer   used to destroy items if actions on them throw exceptions.
     * @throws IllegalArgumentException if {@code 0<=initialSize<=capacity} is violated
     * @throws NullPointerException     if {@code destroyer} is {@code null} or if {@code initialSize>0} and
     *                                  {@link #create()} returned null.
     */
    public LazyBlockingPool(int initialSize, int capacity, ThrowingConsumer<? super T, ?> destroyer) {
        if (initialSize > capacity) {
            throw new IllegalArgumentException("initialSize must be less or equal to maxSize");
        }
        if (initialSize < 0) {
            throw new IllegalArgumentException("initialSize must not be negative");
        }
        this.capacity = capacity;
        final AtomicInteger counter = new AtomicInteger();
        this.size = counter;
        this.destroyer = decorate(destroyer, counter);

        this.permissionToUseItem = new Semaphore(capacity);

        this.itemsNotInUse = new ArrayBlockingQueue<>(capacity);
        for (int i = 0; i < initialSize; i++) {
            itemsNotInUse.add(createInternal());
        }

        // if this pool is not properly closed and gets garbage collected, all items will be destroyed.
        this.cleanable = Cleaning.DEFAULT_CLEANER.register(this, new CleaningAction<>(this));
    }

    private static <U> Consumer<U> decorate(final ThrowingConsumer<U, ?> destroyer, AtomicInteger counter) {
        Objects.requireNonNull(destroyer);
        return item -> {
            try {
                counter.decrementAndGet();
                destroyer.accept(item);
            } catch (Exception e) {
                // We will log and ignore exceptions during destruction, because we tried to destroy the item, then it
                // won't be reused anyway, and we can throw it away if the destroyer throws an exception.
                LOG.log(Level.ERROR, "Pooled object could not be fully destroyed due to an exception.", e);
            }
        };
    }

    private T createInternal() {
        size.incrementAndGet();
        return Objects.requireNonNull(this.create());
    }

    /**
     * Called whenever the constructor or {@link #withItem(ThrowingFunction)} needs to create a new item for the pool.
     *
     * @return A new item to use in the pool. Must not be null.
     */
    protected abstract T create();

    protected boolean isValid(T item) {
        return true;
    }

    @Override
    public int size() {
        return this.size.get();
    }

    @Override
    public int capacity() {
        return capacity;
    }

    /**
     * Borrows an item from the pool to execute the given action on it, returning it to the pool afterwards.
     * <p>
     * Creates a new item if all existing items in the pool are currently in use and the capacity has not been reached
     * yet. If all items are in use and the capacity has been reached, the method blocks until an item becomes available
     * or the thread gets interrupted.
     * <p>
     * If an item needs to be created, but {@link #create()} returns {@code null}, a {@link NullPointerException} will
     * be thrown. If {@code create()} throws an exception, it will be rethrown.
     * <p>
     * If the action throws an exception, then the item that was passed to it will be destroyed and not returned to the
     * pool. The exception is then rethrown.
     * <p>
     * If the destroyer throws an exception, it will be swallowed and logged (but the item still won't be returned to
     * the pool).
     *
     * @param action an action to perform with the item.
     * @param <V>    the type of the result of the action.
     * @param <EX>   exceptions the action is allows to throw
     * @return the result of the action on one of the items in this pool.
     * @throws IllegalStateException if this pool is already closed.
     * @throws NullPointerException  if {@code action} is {@code null} or if a new item needed to be created for the
     *                               action but {@link #create()} returned {@code null}.
     * @throws InterruptedException  if the current thread gets interrupted while waiting for an item to become
     *                               available.
     * @apiNote The action MUST not let the item passed to it escape. Otherwise, non-predictable behaviour can occur. In
     * particular: Other calls to this method may re-use the item concurrently, or it may get destroyed concurrently at
     * any moment.
     */
    @Override
    public <V, EX extends Exception> V withItem(ThrowingFunction<T, V, EX> action) throws InterruptedException, EX {
        permissionToUseItem.acquire();

        if (closed.get()) {
            permissionToUseItem.release();
            throw new IllegalStateException("BlockingPool closed, all items destroyed.");
        }

        T item = null;
        try {
            item = borrowFromPool();
            final V result = action.apply(item);
            returnToPool(item);
            return result;
        } catch (Exception ex) {
            if (item != null) {
                destroyer.accept(item);
            }
            throw ex;
        } finally {
            permissionToUseItem.release();
        }
    }

    private T borrowFromPool() {
        return Objects.requireNonNullElseGet(pollUntilValidOrEmpty(), this::createInternal);
    }

    private T pollUntilValidOrEmpty() {
        T item;
        while (true) {
            item = itemsNotInUse.poll();
            if (item != null && !isValid(item)) {
                destroyer.accept(item);
                continue;
            }
            return item;
        }
    }

    private void returnToPool(T item) {
        if (isValid(item)) {
            itemsNotInUse.add(item);
        } else {
            destroyer.accept(item);
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                // closed is now true, so all future calls to withItem(..) fail. Now we block here until concurrent
                // executions that were still running when this method got called have finished so that we don't
                // destroy items that are still in use.
                permissionToUseItem.acquire(capacity);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                cleanable.clean();
                permissionToUseItem.release(capacity);
            }
        }
    }

    private static class CleaningAction<T> implements Runnable {
        private final Collection<T> items;
        private final Consumer<? super T> destroyer;

        public CleaningAction(LazyBlockingPool<T> pool) {
            this.items = pool.itemsNotInUse;
            this.destroyer = pool.destroyer;
        }

        @Override
        public void run() {
            // Exceptions thrown by the destroyer are logged and swallowed, see {@link #decorate(Consumer)}, so we
            // will not break out of the loop early.
            this.items.forEach(destroyer);
        }
    }
}
