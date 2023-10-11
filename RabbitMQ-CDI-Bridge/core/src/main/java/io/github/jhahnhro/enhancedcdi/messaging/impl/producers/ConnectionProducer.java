package io.github.jhahnhro.enhancedcdi.messaging.impl.producers;

import static java.util.Objects.requireNonNullElse;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.util.concurrent.TimeoutException;
import javax.enterprise.context.ApplicationScoped;
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

    private static void logFailure(int nr, Exception ex, String formatString) {
        final String msg = String.format(formatString, nr,
                                         requireNonNullElse(ex.getMessage(), ex.getClass().getSimpleName()));
        if (LOG.isLoggable(Level.DEBUG)) {
            LOG.log(Level.DEBUG, msg, ex);
        } else {
            LOG.log(Level.WARNING, msg + " Set log level to DEBUG for full stack trace.");
        }
    }

    @Produces
    @ApplicationScoped
    Connection produceConnection(Configuration configuration) throws InterruptedException, TimeoutException {
        return newConnection(configuration);
    }

    void disposeConnection(@Disposes Connection connection) {
        LOG.log(Level.INFO, "Shutting down connection to RabbitMQ broker...");
        try {
            connection.close();
            LOG.log(Level.INFO, "Connection to RabbitMQ broker successfully shut down.");
        } catch (AlreadyClosedException sse) {
            LOG.log(Level.DEBUG, "Connection to RabbitMQ broker was already shut down");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Connection newConnection(Configuration configuration) throws InterruptedException, TimeoutException {
        final Connection connection;
        try {
            LOG.log(Level.INFO, "Opening connection to RabbitMQ broker...");
            connection = newConnectionWithRetry(configuration);
            LOG.log(Level.INFO, "Connection to RabbitMQ broker successfully opened.");
        } catch (TimeoutException | InterruptedException e) {
            LOG.log(Level.ERROR, "Connection to RabbitMQ broker was not established", e);
            throw e;
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
        final String formatString = getMsgForFailedAttempt(retry);

        return retry.call(() -> configuration.connectionFactory().newConnection(),
                          (attemptNr, exception) -> logFailure(attemptNr, exception, formatString));
    }

    private String getMsgForFailedAttempt(Retry retry) {
        if (retry.maxAttempts() == Integer.MAX_VALUE) {
            return "Attempt %d failed: %s.";
        } else {
            return "Attempt %d of " + retry.maxAttempts() + " failed: %s.";
        }
    }

}