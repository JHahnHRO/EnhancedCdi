package io.github.jhahnhro.enhancedcdi.messaging.impl.producers;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.CreationException;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Connection;
import io.github.jhahnhro.enhancedcdi.messaging.Configuration;
import io.github.jhahnhro.enhancedcdi.messaging.Retry;

@Singleton
class ConnectionProducer {

    private static final System.Logger LOG = System.getLogger(ConnectionProducer.class.getCanonicalName());

    @Produces
    @ApplicationScoped
    Connection produceConnection(Configuration configuration) {
        return newConnection(configuration);
    }

    void disposeConnection(@Disposes Connection connection) throws IOException {
        LOG.log(Level.INFO, "Shutting down connection to RabbitMQ broker...");
        try {
            connection.close();
        } catch (AlreadyClosedException sse) {
            LOG.log(Level.DEBUG, "Connection to RabbitMQ broker was already shut down");
        }
    }

    private Connection newConnection(Configuration configuration) {
        final Connection connection;
        try {
            LOG.log(Level.INFO, "Opening connection to RabbitMQ broker...");
            connection = newConnectionWithRetry(configuration);
            LOG.log(Level.INFO, "Connection to RabbitMQ broker successfully opened.");
        } catch (TimeoutException e) {
            throw new CreationException("Connection to RabbitMQ broker could not be established", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CreationException(
                    "Connection to RabbitMQ broker was not established, because the thread was interrupted", e);
        }

        connection.addShutdownListener(shutdownSignal -> {
            if (shutdownSignal.isInitiatedByApplication()) {
                LOG.log(Level.INFO, "Connection to RabbitMQ broker shut down");
            } else {
                LOG.log(Level.WARNING, "Connection to RabbitMQ broker shut down unexpectedly", shutdownSignal);
            }
        });
        return connection;
    }

    private Connection newConnectionWithRetry(Configuration configuration)
            throws TimeoutException, InterruptedException {

        final Retry retry = configuration.initialConnectionRetry();
        final String msg = getMsgForFailedAttempt(retry);
        final BiConsumer<Integer, Exception> logFailures = (nr, e) -> LOG.log(Level.INFO,
                                                                              msg.formatted(nr, e.getMessage()));
        return retry.call(() -> configuration.connectionFactory().newConnection(), logFailures);
    }

    private String getMsgForFailedAttempt(Retry retry) {
        if (retry.maxAttempts() == Integer.MAX_VALUE) {
            return "Attempt %d failed: %s.";
        } else {
            return "Attempt %d of " + retry.maxAttempts() + " failed: %s.";
        }
    }

}