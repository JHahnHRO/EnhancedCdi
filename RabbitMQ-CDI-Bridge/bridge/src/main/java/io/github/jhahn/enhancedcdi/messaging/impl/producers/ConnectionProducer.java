package io.github.jhahn.enhancedcdi.messaging.impl.producers;

import com.rabbitmq.client.Connection;
import io.github.jhahn.enhancedcdi.messaging.Configuration;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.CreationException;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.util.concurrent.TimeoutException;

/**
 * TODO clarify responsibilities between ConnectionContextController & ConnectionProducer
 */
@Singleton
class ConnectionProducer {

    private static final System.Logger LOG = System.getLogger(ConnectionProducer.class.getCanonicalName());

    @Produces
    @ApplicationScoped
    Connection produceConnection(Configuration configuration) {
        return newConnection(configuration);
    }

    void disposeConnection(@Disposes Connection connection) throws IOException {
        if (connection.isOpen()) {
            LOG.log(Level.INFO, "Shutting down connection to RabbitMQ broker...");
            connection.close();
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
        final Configuration.Retry retry = configuration.initialConnectionRetry();
        final String ofMaxAttempts = retry.maxAttempts() == Integer.MAX_VALUE ? "" : " of " + retry.maxAttempts();

        final long deadline = System.nanoTime() + retry.maxWaitingTime().toNanos();

        long delay = retry.initialDelay().toNanos();
        for (int attempt = 1; attempt <= retry.maxAttempts(); attempt++) {
            try {
                return configuration.connectionFactory().newConnection();
            } catch (IOException | TimeoutException ex) {
                LOG.log(Level.INFO, "Connection attempt %d%s failed: %s", attempt, ofMaxAttempts, ex.getMessage());
            }

            // calculate next delay with exponential backoff
            delay = Math.min(delay * 2, retry.maxDelay().toNanos());

            final long now = System.nanoTime();
            if (now + delay > deadline) {
                throw new TimeoutException("Maximum wait time (which is " + retry.maxWaitingTime() + ") reached");
            }

            Thread.sleep(delay / 1_000_000);
        }
        throw new TimeoutException("Maximum number of attempts (which is " + retry.maxAttempts() + ") reached");
    }

}