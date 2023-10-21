package io.github.jhahnhro.enhancedcdi.pooled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class AbstractBlockingPoolTest {

    private AbstractBlockingPool<Object> pool = new TestPool(8);

    private static class TestPool extends AbstractBlockingPool<Object> {
        /**
         * Constructs a new instance with the given capacity.
         *
         * @param capacity the capacity this pool will have. Must be non-negative.
         * @throws IllegalArgumentException if {@code capacity<0}
         */
        protected TestPool(int capacity) {
            super(capacity);
        }

        @Override
        protected Object borrowFromPool() throws InterruptedException {
            return null;
        }

        @Override
        protected void returnToPool(Object item) {

        }
    }

    @Nested
    class TestFullPoolLock {

        @Timeout(value = 1, unit = TimeUnit.SECONDS)
        @Test
        void whenLockingTwice_thenNoDeadlock() {
            final Lock lock = pool.getLock();

            lock.lock();
            assertThat(lock.tryLock()).isTrue();

            lock.unlock();
            lock.unlock();
        }

        @Test
        void givenNotLocked_whenUnlock_thenThrowIMSE() {
            final Lock lock = pool.getLock();

            assertThatThrownBy(lock::unlock).isInstanceOf(IllegalMonitorStateException.class);
        }

        @Test
        void givenLocked_whenUnlockTwice_thenThrowIMSE() {
            final Lock lock = pool.getLock();

            lock.lock();
            lock.unlock();
            assertThatThrownBy(lock::unlock).isInstanceOf(IllegalMonitorStateException.class);
        }

    }

}

