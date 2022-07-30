package io.github.jhahnhro.enhancedcdi.pooled;

import io.github.jhahnhro.enhancedcdi.util.Cleaning;

import java.lang.ref.Cleaner;
import java.util.Collection;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @param <T>
 */
public class PoolImpl<T> implements Pool<T> {

    /**
     * Used to getOrCreate new items for the pool
     */
    private final Supplier<? extends T> creator;
    /**
     * Used to destroy items when the pool gets closed or an action passed to {@link #withItem(ThrowingFunction)} threw an
     * exception.
     */
    private final Consumer<? super T> destroyer;

    /**
     * The items that are not currently in use inside {@link #withItem(ThrowingFunction)}
     */
    private final Queue<T> itemsNotInUse;
    private final Semaphore permitToUseItem;

    /**
     * An estimate for the current size of the pool. Gets incremented by {@link #creator} and decremented by
     * {@link #destroyer}.
     */
    private final AtomicInteger size;

    private final int maxSize;
    /**
     * Whether this pool is closed.
     */
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * ThrowingFunction to execute when this pool gets closed or garbage collected.
     */
    private final Cleaner.Cleanable cleanable;

    public PoolImpl(int minSize, int maxSize, Supplier<? extends T> creator, Consumer<? super T> destroyer) {
        this.maxSize = maxSize;
        AtomicInteger size = new AtomicInteger(0);
        this.creator = decorate(creator, size);
        this.destroyer = decorate(destroyer, size);
        this.size = size;

        if (minSize > maxSize) {
            throw new IllegalArgumentException("minSize must be less or equal to maxSize");
        }
        if (minSize < 0) {
            throw new IllegalArgumentException("minSize must not be negative");
        }

        this.itemsNotInUse = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < minSize; i++) {
            itemsNotInUse.offer(Objects.requireNonNull(creator.get()));
        }
        this.permitToUseItem = new Semaphore(maxSize);

        // if this pool is not properly closed and gets garbage collected, all items will be destroyed.
        this.cleanable = Cleaning.DEFAULT_CLEANER.register(this, new CleaningAction<>(this));
    }

    private static <U> Supplier<U> decorate(final Supplier<U> creator, final AtomicInteger size) {
        Objects.requireNonNull(creator);
        return () -> {
            U item = creator.get();
            size.incrementAndGet();
            return item;
        };
    }

    private static <U> Consumer<U> decorate(final Consumer<U> destroyer, final AtomicInteger size) {
        Objects.requireNonNull(destroyer);
        return item -> {
            size.decrementAndGet();
            destroyer.accept(item);
        };
    }

    @Override
    public int size() {
        return size.get();
    }

    @Override
    public <V, EX extends Exception> V withItem(ThrowingFunction<T, V, EX> action) throws InterruptedException, EX {
        if (closed.get()) {
            throw new IllegalStateException("Pool closed, all items destroyed.");
        }

        permitToUseItem.acquire();
        T item = null;
        try {
            item = Objects.requireNonNullElseGet(itemsNotInUse.poll(), creator);
            try {
                return action.apply(item);
            } catch (Exception ex) {
                final T item2 = item;
                item = null; // do not return this item to the pool, even if the next line throws an exception
                destroyer.accept(item2);
                throw ex;
            }
        } finally {
            if (item != null) {
                itemsNotInUse.offer(item);
            }
            permitToUseItem.release();
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                // closed is now true, so all future calls to withItem(..) fail. Now we block here until concurrent
                // executions that were still running when this method got called have finished so that we don't
                // destroy items that are still in use.
                permitToUseItem.acquire(maxSize);
                cleanable.clean();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                // strictly speaking it does not matter if we release the semaphore again, because no one will ever
                // try to acquire it after this, but let's be good citizens and clean up after ourselves
                permitToUseItem.release(maxSize);
            }
        }
    }

    private static class CleaningAction<T> implements Runnable {
        private final Collection<T> items;
        private final Consumer<? super T> destroyer;

        public CleaningAction(PoolImpl<T> pool) {
            this.items = pool.itemsNotInUse;
            this.destroyer = pool.destroyer;
        }

        @Override
        public void run() {
            boolean throwMe = false;
            RuntimeException exception = new RuntimeException(
                    "CleaningAction could not destroy all items in the pool.");
            for (T item : this.items) {
                // Try in a loop to give every element a chance to be destroyed, even if some don't make it.
                try {
                    destroyer.accept(item);
                } catch (Exception ex) {
                    exception.addSuppressed(ex);
                    throwMe = true;
                }
            }
            if (throwMe) {
                throw exception;
            }
        }
    }
}
