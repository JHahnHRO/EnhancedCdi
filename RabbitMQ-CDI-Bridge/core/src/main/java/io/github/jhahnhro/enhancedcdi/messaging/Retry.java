package io.github.jhahnhro.enhancedcdi.messaging;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

/**
 * A configuration for retrying a failed operation.
 */
public record Retry(Duration initialDelay, Duration maxDelay, Duration maxWaitingTime, int maxAttempts) {

    public static final Retry NO_RETRY = new Retry(Duration.ZERO, Duration.ZERO, Duration.ZERO, 1);

    public Retry {
        Objects.requireNonNull(initialDelay);
        Objects.requireNonNull(maxDelay);
        Objects.requireNonNull(maxWaitingTime);
        if (initialDelay.isNegative()) {
            throw new IllegalArgumentException("initialDelay must be non-negative");
        }
        if (maxDelay.compareTo(initialDelay) < 0) {
            throw new IllegalArgumentException("maxDelay must be at least initialDelay");
        }
        if (maxWaitingTime.isNegative()) {
            throw new IllegalArgumentException("maxWaitingTime must be non-negative");
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
    }

    /**
     * Convenience method returning an instance to use as a starting point for the fluent interface.
     * <p>
     * Example:
     * <pre>{@code
     * var retry = Retry.after(Duration.ofSeconds(5))
     *                  .withMaxDelay(Duration.ofMinutes(1))
     *                  .giveUpAfter(Duration.ofMinutes(10))
     *                  .giveUpAfterAttemptNr(100);
     * }</pre>
     *
     * @return an instance to use as a starting point for the fluent interface.
     */
    public static Retry after(Duration delay) {
        return new Retry(delay, delay, Duration.ofSeconds(Long.MAX_VALUE), Integer.MAX_VALUE);
    }

    public Retry withMaxDelay(Duration maxDelay) {
        return new Retry(this.initialDelay, maxDelay, this.maxWaitingTime, this.maxAttempts);
    }

    public Retry giveUpAfterAttemptNr(int maxAttempts) {
        return new Retry(this.initialDelay, this.maxDelay, this.maxWaitingTime, maxAttempts);
    }

    public Retry giveUpAfter(Duration maxWaitingTime) {
        return new Retry(this.initialDelay, this.maxDelay, maxWaitingTime, this.maxAttempts);
    }

    public Retry indefinitely() {
        return new Retry(this.initialDelay, this.maxDelay, Duration.ofSeconds(Long.MAX_VALUE), Integer.MAX_VALUE);
    }

    /**
     * Computes the next delay duration according to the exponential back-off strategy.
     * <p>
     * If the given value is smaller than {@link #initialDelay}, then {@code initialDelay} is returned. If the
     * exponential back-off would take the next delay value over {@link #maxDelay}, then {@code maxDelay} is returned.
     *
     * @param prevDelay previous delay
     * @return the next delay.
     */
    public Duration calcNextDelay(Duration prevDelay) {
        if (prevDelay.compareTo(initialDelay) < 0) {
            return initialDelay;
        }
        // We do not use prevDelay.multipliedBy(2) in the condition, because that might result in an overflow. However,
        // minor complication ahead: This is integer division, so it is rounding down. Therefore, we need to consider
        // two cases:
        // 1. if maxDelay is an even number of nanoseconds, then this condition is equivalent to
        //    prevDelay > maxDelay/2 \iff 2*prevDelay > maxDelay
        // 2. if maxDelay is an odd number of nanoseconds, then this condition is equivalent to
        //    prevDelay > (maxDelay-1)/2 \iff 2*prevDelay > maxDelay-1 \iff 2*prevDelay >= maxDelay
        // In both cases, maxDelay is the correct value to return.
        if (prevDelay.compareTo(maxDelay.dividedBy(2)) > 0) {
            return maxDelay;
        }
        return prevDelay.multipliedBy(2);
    }

    /**
     * Like {@link #call(Callable, BiConsumer)} but with a no-op {@code exceptionHandler}.
     */
    public <V> V call(final Callable<V> callable) throws TimeoutException, InterruptedException {
        return call(callable, (i, e) -> {});
    }

    /**
     * Executes the given {@link Callable} in the current thread and applies this retry-strategy if that failed with an
     * exception. The {@code Callable} will be called again until either
     * <ul>
     *     <li>it successfully returns a value (may be null) or</li>
     *     <li>the {@link #maxAttempts() maximum number of attempts} has been reached or</li>
     *     <li>the {@link #maxWaitingTime() maximum waiting time} has been reached or</li>
     *     <li>the current thread is interrupted</li>
     * </ul>
     * The current thread will block until either of these conditions is satisfied.
     * <p>
     * Note that the {@code Callable} is always invoked at least once, even if {@link #NO_RETRY no retry} at all is
     * applied. Also note that the computation will not be cancelled if the first call already exceeds the
     * {@link #maxWaitingTime}. Only between any two invocations are the conditions checked.
     *
     * @param callable         the Callable to execute. Must not be null.
     * @param exceptionHandler An additional action to be performed when an attempt fails with an exception. The number
     *                         of the attempt and the exception is given to the BiConsumer.
     * @param <V>              the result type.
     * @return the result of the given Callable when it finally succeeds.
     * @throws TimeoutException     if the maximum number of attempts or the maximum waiting time has been reached
     *                              before the {@link Callable} produced a result.
     * @throws InterruptedException if the thread is interrupted while waiting between two attempts.
     */
    public <V> V call(final Callable<V> callable, final BiConsumer<Integer, Exception> exceptionHandler)
            throws TimeoutException, InterruptedException {
        Objects.requireNonNull(callable);
        Objects.requireNonNull(exceptionHandler);

        final Instant begin = now();
        // deadline = begin + maxWaitingTime

        Instant now = begin;
        Duration delay = initialDelay;
        int attempt = 1;
        while (attempt <= maxAttempts && notAfterDeadline(begin, now)) {
            try {
                return callable.call();
            } catch (Exception ex) {
                exceptionHandler.accept(attempt, ex);
            }

            if (attempt < maxAttempts) {
                sleep(delay);
            }

            now = now();
            delay = calcNextDelay(delay);
            attempt++;
        }

        if (attempt > maxAttempts) {
            throw new TimeoutException("Maximum number of attempts %d was reached".formatted(maxAttempts));
        }
        throw new TimeoutException("Maximum wait time of %s was reached".formatted(maxWaitingTime));
    }

    /**
     * package-private method that gets bytecode-magic'd by Mockito so that we can mock the system time.
     *
     * @return the current time.
     */
    Instant now() {
        return Instant.now();
    }

    /**
     * package-private method that gets bytecode-magic'd by Mockito so that we can mock Thread#sleep.
     *
     * @param sleepTime the duration to sleep.
     * @throws InterruptedException if interrupted while sleeping.
     */
    void sleep(Duration sleepTime) throws InterruptedException {
        Thread.sleep(sleepTime.toMillis());
    }

    private boolean notAfterDeadline(final Instant begin, final Instant now) {
        // deadline = begin + maxWaitingTime
        //
        // Mathematically: now <= deadline \iff (now - begin) <= maxWaitingTime
        // However, the right hand side prevents overflow, so we use that.
        return Duration.between(begin, now).compareTo(maxWaitingTime) <= 0;
    }

    public void run(Runnable runnable) throws InterruptedException, TimeoutException {
        call(() -> {
            runnable.run();
            return null;
        });
    }

}
