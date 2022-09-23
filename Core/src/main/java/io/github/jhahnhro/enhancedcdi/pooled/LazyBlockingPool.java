package io.github.jhahnhro.enhancedcdi.pooled;

import io.github.jhahnhro.enhancedcdi.util.Cleaning;

import java.lang.System.Logger.Level;
import java.lang.ref.Cleaner;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
     * Items in this pool that are currently in use.
     */
    protected final Collection<T> itemsInUse;
    /**
     * Guards the following invariant: {@link #itemsInUse} and {@link #itemsNotInUse} are disjoint and their sizes add
     * up to {@link #size}.
     */
    protected final ReadWriteLock lock;
    /**
     * A semaphore with {@link #capacity} permits.
     */
    private final Semaphore permissionToUseItem;

    private final int capacity;
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
     */
    public LazyBlockingPool(int initialSize, int capacity, ThrowingConsumer<? super T, ?> destroyer) {
        if (initialSize > capacity) {
            throw new IllegalArgumentException("initialSize must be less or equal to maxSize");
        }
        if (initialSize < 0) {
            throw new IllegalArgumentException("initialSize must not be negative");
        }
        this.capacity = capacity;

        this.destroyer = decorate(destroyer);

        this.permissionToUseItem = new Semaphore(capacity);

        this.itemsNotInUse = new LinkedList<>();
        this.itemsInUse = Collections.newSetFromMap(new IdentityHashMap<>(capacity));
        this.lock = new ReentrantReadWriteLock();
        for (int i = 0; i < initialSize; i++) {
            itemsNotInUse.add(Objects.requireNonNull(this.create()));
        }

        // if this pool is not properly closed and gets garbage collected, all items will be destroyed.
        this.cleanable = Cleaning.DEFAULT_CLEANER.register(this, new CleaningAction<>(this));
    }

    private static <U> Consumer<U> decorate(final ThrowingConsumer<U, ?> destroyer) {
        Objects.requireNonNull(destroyer);
        return item -> {
            try {
                destroyer.accept(item);
            } catch (Exception e) {
                // We will log and ignore exceptions during destruction, because we tried to destroy the item, then it
                // won't be reused anyway, and we can throw it away if the destroyer throws an exception.
                LOG.log(Level.ERROR, "Pooled object could not be fully destroyed due to an exception.", e);
            }
        };
    }

    /**
     * Called whenever {@link #withItem(ThrowingFunction)} needs to borrow an item from the pool, all existing items are
     * currently in use, but the maximum capacity has not yet been reached.
     *
     * @return A new item to use in the pool. Must not be null.
     */
    protected abstract T create();

    @Override
    public int size() {
        try {
            lock.readLock().lock();
            return this.itemsInUse.size() + this.itemsNotInUse.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int capacity() {
        return capacity;
    }

    /**
     * Borrows an item from the pool to execute the given action on it, returning it to the pool afterwards.
     * <p>
     * Creates a new item if all items in the pool are currently in use and the capacity has not been reached yet. If
     * all items are in use and the maximum size has been reached, the method blocks until an item becomes free to use
     * or the thread gets interrupted.
     * <p>
     * If an item needs to be created, but the {@link Supplier} returns {@code null}, a {@link NullPointerException}
     * will be thrown. If the supplier throws an exception, it will be rethrown.
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
     * @throws IllegalStateException if the pool is already closed.
     * @throws NullPointerException  if {@code action} is {@code null}.
     * @throws InterruptedException  if the current thread gets interrupted while waiting for a free item.
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

        T item = borrowFromPool();
        try {
            final V result = action.apply(item);
            returnToPool(item);
            return result;
        } catch (Exception ex1) {
            destroyer.accept(item);
            throw ex1;
        } finally {
            permissionToUseItem.release();
        }
    }

    private T borrowFromPool() {
        try {
            lock.writeLock().lock();
            T item = Objects.requireNonNullElseGet(itemsNotInUse.poll(), this::create);
            itemsInUse.add(item);
            return item;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void returnToPool(T item) {
        try {
            lock.writeLock().lock();
            final boolean wasInUse = itemsInUse.remove(item);
            if (wasInUse) {
                // if the item has been removed from #itemsInUse since #borrowFromPool was called, this means that
                // #removeUnusableItem is to blame, i.e. the item became unusable while the action was performed.
                // in this case, we will not return it to the pool.
                itemsNotInUse.add(item);
            }
        } finally {
            lock.writeLock().unlock();
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
                cleanable.clean();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                permissionToUseItem.release(capacity);
            }
        }
    }

    /**
     * Can be called to remove all items from the pool if all items become unusable for some external reason. They will
     * not be explicitly destroyed, but returned.
     *
     * @return all items previously contained in this pool.
     */
    protected Collection<T> clear() {
        try {
            lock.writeLock().lock();
            Set<T> items = Collections.newSetFromMap(new IdentityHashMap<>());
            items.addAll(itemsInUse);
            itemsInUse.clear();
            items.addAll(itemsNotInUse);
            itemsNotInUse.clear();

            return items;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Can be called if an item because unusable for some external reason. It will be removed from the pool and not be
     * used again in {@link #withItem(ThrowingFunction)}. It will not be explicitly destroyed. If the item is currently
     * in use, it will not be returned to the pool afterwards.
     *
     * @param item an item from the pool that is unusable.
     */
    protected void removeUnusableItem(T item) {
        try {
            lock.writeLock().lock();
            final boolean isCurrentlyInUse = itemsInUse.remove(item);

            if (!isCurrentlyInUse) {
                // use iterator instead of Collection#remove, because we need identity comparison, not equals()
                // comparison.
                for (var iterator = itemsNotInUse.iterator(); iterator.hasNext(); ) {
                    T t = iterator.next();
                    if (t == item) {
                        iterator.remove();
                        break;
                    }
                }
            }
        } finally {
            lock.writeLock().unlock();
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
