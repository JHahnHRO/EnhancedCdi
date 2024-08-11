package io.github.jhahnhro.enhancedcdi.messaging.impl.producers;

import static java.util.Objects.requireNonNullElse;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownNotifier;
import com.rabbitmq.client.ShutdownSignalException;
import io.github.jhahnhro.enhancedcdi.messaging.Configuration;
import io.github.jhahnhro.enhancedcdi.messaging.Retry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

@Singleton
class ConnectionProducer {

    private static final System.Logger LOG = System.getLogger(ConnectionProducer.class.getCanonicalName());

    @Produces
    @ApplicationScoped
    BookkeepingConnection produceConnection(Configuration configuration) throws InterruptedException, TimeoutException {
        return new ChannelTrackingConnection(newConnection(configuration));
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


    private void logFailure(int nr, Exception ex, String formatString) {
        final String msg = String.format(formatString, nr,
                                         requireNonNullElse(ex.getMessage(), ex.getClass().getSimpleName()));
        if (LOG.isLoggable(Level.DEBUG)) {
            LOG.log(Level.DEBUG, msg, ex);
        } else {
            LOG.log(Level.WARNING, msg + " Set log level to DEBUG for full stack trace.");
        }
    }

    private static class ChannelTrackingConnection extends ConnectionDecorator implements BookkeepingConnection {
        private final TrackingSemaphore channelPermit;

        ChannelTrackingConnection(Connection internalConnection) {
            super(internalConnection);
            this.channelPermit = new TrackingSemaphore(internalConnection.getChannelMax());
        }

        @Override
        public Channel createChannel() throws IOException {
            channelPermit.reducePermits(1);
            return getChannelAndEnsureRelease(super::createChannel);
        }

        @Override
        public Channel createChannel(int channelNumber) throws IOException {
            channelPermit.reducePermits(1);
            return getChannelAndEnsureRelease(() -> super.createChannel(channelNumber));
        }

        @Override
        public Channel acquireChannel() throws InterruptedException, IOException {
            channelPermit.acquire(); // the corresponding release is in the shutdownListener
            return getChannelAndEnsureRelease(() -> Objects.requireNonNull(super.createChannel()));
        }

        private Channel getChannelAndEnsureRelease(ChannelSupplier channelSupplier) throws IOException {
            Channel channel;
            try {
                channel = channelSupplier.get();
            } catch (IOException | RuntimeException e) {
                channelPermit.release();
                throw e;
            }
            if (channel == null) {
                channelPermit.release();
            } else {
                channelPermit.ensureReleaseOnShutdown(channel);
            }
            return channel;
        }

        @FunctionalInterface
        private interface ChannelSupplier {
            Channel get() throws IOException;
        }
    }

    private static class TrackingSemaphore extends Semaphore {
        public TrackingSemaphore(final int permits) {super(permits, true);}

        // override to make the protected method available for use
        @Override
        public void reducePermits(int reduction) {
            super.reducePermits(reduction);
        }

        public void ensureReleaseOnShutdown(ShutdownNotifier channelOrConnection) {
            final ShutdownListener listener = new ShutdownListener() {
                private final AtomicBoolean closed = new AtomicBoolean(false);

                @Override
                public void shutdownCompleted(ShutdownSignalException sse) {
                    // workaround because Channel#close is not idempotent in RabbitMQ Java Client 5.x
                    // Will be fixed in 6.x
                    if (closed.compareAndSet(false, true)) {
                        TrackingSemaphore.this.release();
                        channelOrConnection.removeShutdownListener(this);
                    }
                }
            };

            channelOrConnection.addShutdownListener(listener);
        }

    }
}