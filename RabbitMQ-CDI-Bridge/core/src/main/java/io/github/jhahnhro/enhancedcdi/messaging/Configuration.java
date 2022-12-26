package io.github.jhahnhro.enhancedcdi.messaging;

import java.lang.System.Logger.Level;
import java.time.Duration;
import java.util.Objects;

import com.rabbitmq.client.ConnectionFactory;

public record Configuration(ConnectionFactory connectionFactory, Retry initialConnectionRetry, int maxMessageSize) {

    private static final System.Logger LOG = System.getLogger(Configuration.class.getName());

    public Configuration {
        connectionFactory = validate(connectionFactory);
        Objects.requireNonNull(initialConnectionRetry);
        validate(maxMessageSize);
    }

    public Configuration(ConnectionFactory connectionFactory, Retry initialConnectionRetry) {
        this(connectionFactory, initialConnectionRetry, 0x8000000 /* = 2^27 = 128 MiB */);
    }

    private void validate(int maxMessageSize) {
        if (maxMessageSize < 0) {
            throw new IllegalArgumentException("MaxMessageSize must be non-negative");
        }
    }

    private ConnectionFactory validate(ConnectionFactory originalConnectionFactory) {
        final ConnectionFactory connectionFactory = originalConnectionFactory.clone();
        if (!connectionFactory.isAutomaticRecoveryEnabled() || !connectionFactory.isTopologyRecoveryEnabled()) {
            LOG.log(Level.WARNING, "The given connection factory does not have automatic recovery enabled. "
                                   + "If you are using the RabbitMQ-CDI-Bridge, you need that! "
                                   + "It will be enabled now (only for the connection the RabbitMQ-CDI-Bridge uses).");
            connectionFactory.setAutomaticRecoveryEnabled(true);
            connectionFactory.setTopologyRecoveryEnabled(true);
        }
        return connectionFactory;
    }

    /**
     * A configuration for retrying a failed operation.
     *
     * @param initialDelay   duration to wait before attempting the first retry. Must be non-negative. If zero, then the
     *                       operation is retried immediately.
     * @param maxDelay       maximal time gap between two attempts. Must be bigger or equal than {@code initialDelay}.
     *                       An exponential backoff strategy will be used until the gap between two attempts has reached
     *                       the maximum.
     * @param maxWaitingTime the maximum overall time the operation will be retried. Must be non-negative. If zero, then
     *                       the operation is not retried.
     * @param maxAttempts    the maximum number of attempts the operation will be (re)tried. Must be positive. If equal
     *                       to one, then the operation is not retried.
     */
    public record Retry(Duration initialDelay, Duration maxDelay, Duration maxWaitingTime, int maxAttempts) {

        public static Retry NO_RETRY = new Retry(Duration.ZERO, Duration.ZERO, Duration.ZERO, 1);

        public Retry {
            Objects.requireNonNull(initialDelay);
            Objects.requireNonNull(maxDelay);
            Objects.requireNonNull(maxWaitingTime);
            if (initialDelay.compareTo(Duration.ZERO) < 0) {
                throw new IllegalArgumentException("initialDelay must be non-negative");
            }
            if (maxDelay.compareTo(initialDelay) < 0) {
                throw new IllegalArgumentException("maxDelay must be at least initialDelay");
            }
            if (maxWaitingTime.compareTo(Duration.ZERO) < 0) {
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
         * var retry = Retry.every(Duration.ofSeconds(5))
         *                  .withMaxDelay(Duration.ofMinutes(1))
         *                  .giveUpAfter(Duration.ofMinutes(10))
         *                  .giveUpAfterAttemptNr(100);
         * }</pre>
         *
         * @return an instance to use as a starting point for the fluent interface.
         */
        public static Retry every(Duration delay) {
            return new Retry(delay, delay, Duration.ofSeconds(Long.MAX_VALUE), Integer.MAX_VALUE);
        }

        public Retry withInitialDelay(Duration initialDelay) {
            return new Retry(initialDelay, this.maxDelay, this.maxWaitingTime, this.maxAttempts);
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
    }
}

