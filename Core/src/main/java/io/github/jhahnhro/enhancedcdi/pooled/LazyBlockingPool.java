package io.github.jhahnhro.enhancedcdi.pooled;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * An implementation of {@link BlockingPool} that creates items lazily on-demand if the capacity of this pool has not
 * yet been reached.
 *
 * @param <T> type of pooled objects
 */
public class LazyBlockingPool<T> extends AbstractBlockingPool<T> {

    /**
     * The items that are not currently in use inside {@link #apply(ThrowingFunction)}
     */
    protected final BlockingQueue<ItemAndCreationTime<T>> itemsNotInUse;
    private final Lifecycle<T> itemLifecycle;
    private final ScheduledExecutorService executorService;
    private final Duration keepAliveTime;

    // package-private and non-final to mock time in tests
    InstantSource clock;

    /**
     * Creates a new instance.
     *
     * @param initialSize   number of items to create initially.
     * @param capacity      maximum number of items in the pool.
     * @param itemLifecycle the lifecycle of items, defining how they are created and destroyed and if they are still
     *                      usable.
     * @throws NullPointerException     if {@code itemLifecycle} is {@code null}.
     * @throws IllegalArgumentException if {@code 0<=initialSize<=capacity} is violated or
     *                                  {@code itemLifecycle.keepAlive()} is negative.
     * @throws InterruptedException     if the current thread is interrupted while waiting for the pool to fill to its
     *                                  initial size. Will not happen if {@code initialSize == 0}.
     */
    public LazyBlockingPool(int initialSize, int capacity, Lifecycle<T> itemLifecycle) throws InterruptedException {
        this(initialSize, capacity, itemLifecycle, InstantSource.system());
    }

    /**
     * Package-private constructor that sets the clock. Used in tests.
     */
    LazyBlockingPool(int initialSize, int capacity, Lifecycle<T> itemLifecycle, final InstantSource clock)
            throws InterruptedException {
        super(capacity);
        if (initialSize > capacity) {
            throw new IllegalArgumentException("initialSize must be less or equal to maxSize");
        }
        if (initialSize < 0) {
            throw new IllegalArgumentException("initialSize must not be negative");
        }
        this.itemLifecycle = Objects.requireNonNull(itemLifecycle);

        this.keepAliveTime = itemLifecycle.keepAlive().orElse(null);
        if (keepAliveTime != null && keepAliveTime.isNegative()) {
            throw new IllegalArgumentException("keepAlive must not be negative");
        }

        this.executorService = Executors.newSingleThreadScheduledExecutor();

        this.itemsNotInUse = new ArrayBlockingQueue<>(capacity);
        prefillPool(initialSize);

        this.clock = Objects.requireNonNull(clock);
    }

    private void prefillPool(int initialSize) throws InterruptedException {
        for (int i = 0; i < initialSize; i++) {
            itemsNotInUse.add(new ItemAndCreationTime<>(itemLifecycle.createNew(), clock.instant()));
        }
        if (initialSize > 0) {
            scheduleNextHousekeeping();
        }
    }

    private void scheduleNextHousekeeping() {
        if (keepAliveTime != null) {
            this.executorService.schedule(this::retireOldItems, keepAliveTime.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private void retireOldItems() {
        Instant now = clock.instant();
        itemsNotInUse.removeIf(x -> {
            if (Duration.between(x.createdAt(), now).compareTo(keepAliveTime) > 0) {
                itemLifecycle.destroy(x.item());
                return true;
            } else {
                return false;
            }
        });
    }

    @Override
    protected T borrowFromPool() throws InterruptedException {
        T item = pollUntilValidOrEmpty();
        if (item == null) {
            return Objects.requireNonNull(itemLifecycle.createNew());
        } else {
            return item;
        }
    }

    private T pollUntilValidOrEmpty() {
        ItemAndCreationTime<T> x;
        while (null != (x = itemsNotInUse.poll())) {
            if (!itemLifecycle.isUsable(x.item())) {
                itemLifecycle.destroy(x.item());
                continue;
            }
            return x.item();
        }
        return null;
    }

    @Override
    protected void returnToPool(T item) {
        if (itemLifecycle.isUsable(item)) {
            itemsNotInUse.add(new ItemAndCreationTime<>(item, clock.instant()));
            scheduleNextHousekeeping();
        } else {
            itemLifecycle.destroy(item);
        }
    }

    @Override
    protected void onClose() {
        clear();
    }

    public void clear() {
        List<ItemAndCreationTime<T>> list = new ArrayList<>(this.capacity());
        itemsNotInUse.drainTo(list);
        list.forEach(x -> itemLifecycle.destroy(x.item()));
    }

    /**
     * Controls the lifecycle of pooled items. A {@link LazyBlockingPool} calls {@link #createNew()} to create new items
     * on-demand, calls {@link #destroy(Object)} to destroy unusable items.
     *
     * @param <T> Type of pooled items.
     */
    public interface Lifecycle<T> {
        /**
         * Creates a new item for the pool. May block the calling thread is it is necessary to wait for some other
         * process to finish, e.g. if the item is related to a network connection.
         *
         * @return a newly created item for the pool. Must not be null.
         * @throws InterruptedException if creating an item is a blocking operation and the current thread get
         *                              interrupted while waiting for it to finish.
         */
        T createNew() throws InterruptedException;

        /**
         * Detects if an item is still usable. Unusable items will be destroyed.
         *
         * @param item an item from the pool.
         * @return {@code true} iff the given item is still usable for {@link #apply(ThrowingFunction)}.
         */
        default boolean isUsable(T item) {
            return true;
        }

        /**
         * Called when an item is destroyed, e.g. after being recognized as no longer usable or when the pool is
         * closing.
         *
         * @param item an item to be destroyed.
         */
        default void destroy(T item) {
        }

        /**
         * @return the duration an item should be kept alive in the pool while not being used. Must not be negative if
         * present.
         */
        default Optional<Duration> keepAlive() {
            return Optional.empty();
        }
    }

    protected record ItemAndCreationTime<T>(T item, Instant createdAt) {}
}
