package io.github.jhahn.enhancedcdi.messaging.impl.producers;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ShutdownSignalException;
import io.github.jhahn.enhancedcdi.messaging.Configuration;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.CreationException;
import javax.inject.Inject;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@ApplicationScoped
class BrokerConnection extends ForwardingConnection {

    private static final System.Logger LOG = System.getLogger(BrokerConnection.class.getCanonicalName());
    private final Lock lock = new ReentrantLock();
    @Inject
    Configuration configuration;
    private Connection delegate = null;

    private static void onShutdownCompleted(ShutdownSignalException cause) {
        if (cause.isInitiatedByApplication()) {
            LOG.log(Level.INFO, "Connection to RabbitMQ broker shut down");
        } else {
            LOG.log(Level.WARNING, "Connection to RabbitMQ broker unexpectedly shut down", cause);
        }
    }

    private Connection newConnection() throws TimeoutException, InterruptedException {
        LOG.log(Level.INFO, "Creating connection to RabbitMQ broker...");
        final Connection connection = newConnectionWithRetry();
        LOG.log(Level.INFO, "Successfully created connection to RabbitMQ broker.");

        connection.addShutdownListener(BrokerConnection::onShutdownCompleted);
        return connection;
    }

    private Connection newConnectionWithRetry() throws TimeoutException, InterruptedException {
        final Configuration.Retry retry = configuration.initialConnectionRetry();
        final String ofMaxAttempts = retry.maxAttempts() == Integer.MAX_VALUE ? "" : " of " + retry.maxAttempts();

        final long NANOS_PER_MILLI = 1_000_000L;
        final long deadline = System.nanoTime() / NANOS_PER_MILLI + retry.maxWaitingTime().toMillis();

        long delay = retry.initialDelay().toMillis();
        for (int attempt = 1; attempt <= retry.maxAttempts(); attempt++) {
            try {
                return configuration.connectionFactory().newConnection();
            } catch (IOException | TimeoutException ex) {
                LOG.log(Level.INFO, "Connection attempt %d%s failed: %s", attempt, ofMaxAttempts, ex.getMessage());
            }

            // calculate next delay with exponential backoff
            delay = Math.min(delay * 2, retry.maxDelay().toMillis());

            final long now = System.nanoTime() / NANOS_PER_MILLI;
            if (now + delay > deadline) {
                throw new TimeoutException("Maximum wait time reached");
            }

            Thread.sleep(delay);
        }
        throw new TimeoutException("Maximum number of attempts reached");
    }

    @PostConstruct
    void createConnection() {
        getDelegate(); // trigger initial connection
    }

    @PreDestroy
    void disposeConnection() throws IOException {
        lock.lock();
        try {
            if (delegate != null && delegate.isOpen()) {
                delegate.close();
                delegate = null;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Connection getDelegate() {
        lock.lock();
        try {
            if (delegate == null || !delegate.isOpen()) {
                delegate = newConnection();
            }
            return delegate;
        } catch (TimeoutException e) {
            throw new CreationException("Connection to RabbitMQ broker could not be established", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CreationException(
                    "Connection to RabbitMQ broker was not established, because the thread was interrupted", e);
        } finally {
            lock.unlock();
        }
    }
}