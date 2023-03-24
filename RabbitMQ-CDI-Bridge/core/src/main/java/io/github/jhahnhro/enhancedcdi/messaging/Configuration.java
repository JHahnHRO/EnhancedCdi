package io.github.jhahnhro.enhancedcdi.messaging;

import java.lang.System.Logger.Level;
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

}

