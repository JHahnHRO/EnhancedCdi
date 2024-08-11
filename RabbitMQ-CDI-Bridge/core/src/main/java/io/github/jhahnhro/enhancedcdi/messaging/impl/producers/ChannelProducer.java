package io.github.jhahnhro.enhancedcdi.messaging.impl.producers;

import static io.github.jhahnhro.enhancedcdi.messaging.messages.Message.DeliveryMode.PERSISTENT;
import static io.github.jhahnhro.enhancedcdi.messaging.messages.Message.DeliveryMode.TRANSIENT;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ReturnListener;
import com.rabbitmq.client.ShutdownListener;
import io.github.jhahnhro.enhancedcdi.messaging.impl.WithConfirms;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Message.DeliveryMode;
import io.github.jhahnhro.enhancedcdi.messaging.messages.MessageBuilder;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;
import io.github.jhahnhro.enhancedcdi.messaging.messages.ReturnedMessage;
import io.github.jhahnhro.enhancedcdi.pooled.BlockingPool;
import io.github.jhahnhro.enhancedcdi.pooled.LazyBlockingPool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
class ChannelProducer {

    private static final System.Logger LOG = System.getLogger(ChannelProducer.class.getCanonicalName());
    private final ReturnListener returnCallback;
    private final ChannelLifeCycle defaultChannelLifeCycle;
    private final ChannelLifeCycle confirmChannelLifeCycle;

    @Inject
    ChannelProducer(Event<ReturnedMessage> event, BookkeepingConnection connection) {
        this.returnCallback = new ReturnHandler(event);

        this.defaultChannelLifeCycle = new ChannelLifeCycle(connection) {
            @Override
            public Channel createNew() throws InterruptedException {
                final Channel channel = super.createNew();
                channel.addReturnListener(returnCallback);
                return channel;
            }
        };

        this.confirmChannelLifeCycle = new ChannelLifeCycle(connection) {
            @Override
            public Channel createNew() throws InterruptedException {
                final Channel channel = super.createNew();
                try {
                    channel.confirmSelect();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                return channel;
            }
        };
    }

    @Produces
    @Default
    @ApplicationScoped
    BlockingPool<Channel> channelPool() throws InterruptedException {
        LOG.log(Level.DEBUG, "Creating shared pool of channels");
        return new ChannelPool(defaultChannelLifeCycle);
    }

    @Produces
    @Default
    @ApplicationScoped
    ChannelSupplier defaultChannelSupplier() {
        return new ChannelSupplier(defaultChannelLifeCycle);
    }

    @Produces
    @WithConfirms
    @ApplicationScoped
    BlockingPool<Channel> channelPoolWithConfirms() throws InterruptedException {
        LOG.log(Level.DEBUG, "Creating shared pool of channels in confirm-mode");
        return new ChannelPool(confirmChannelLifeCycle);
    }

    @Produces
    @WithConfirms
    @ApplicationScoped
    ChannelSupplier confirmChannelSupplier() {
        return new ChannelSupplier(confirmChannelLifeCycle);
    }

    void dispose(@Disposes @Any BlockingPool<Channel> channelPool) {
        LOG.log(Level.DEBUG, "Shutting down shared pool of channels");
        channelPool.close();
    }

    void dispose(@Disposes @Any ChannelSupplier channelSupplier) {
        channelSupplier.close();
    }

    private static final class ChannelPool implements BlockingPool<Channel> {
        private final Connection connection;
        private final LazyBlockingPool<Channel> delegate;

        ChannelPool(ChannelLifeCycle channelLifeCycle) throws InterruptedException {
            this.connection = channelLifeCycle.connection;
            this.delegate = new LazyBlockingPool<>(0, connection.getChannelMax(), channelLifeCycle);
            this.connection.addShutdownListener(sse -> delegate.clear());
        }

        @Override
        public <V, EX extends Exception> V apply(ThrowingFunction<Channel, V, EX> action)
                throws InterruptedException, EX {
            // in case the connection bean has been destroyed (not closed) and re-created since the last time, we
            // re-size the pool to the new capacity. This is a no-op if the capacity has not changed.
            delegate.resize(connection.getChannelMax());
            return delegate.apply(action);
        }

        @Override
        public int capacity() {
            return delegate.capacity();
        }

        @Override
        public void close() {
            delegate.close();
        }
    }

    private static class ChannelLifeCycle implements LazyBlockingPool.Lifecycle<Channel> {
        private final BookkeepingConnection connection;

        public ChannelLifeCycle(BookkeepingConnection connection) {
            this.connection = connection;
        }

        @Override
        public void destroy(Channel channel) {
            try {
                LOG.log(Level.DEBUG, "Destroying channel " + channel + "...");
                channel.abort();
                LOG.log(Level.DEBUG, "Channel destroyed.");
            } catch (AlreadyClosedException sse) {
                LOG.log(Level.DEBUG, "Channel was already closed.");
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Channel " + channel + " was not closed properly. Continuing anyway.", e);
            }
        }

        @Override
        public Channel createNew() throws InterruptedException {
            final Channel channel;
            try {
                LOG.log(Level.DEBUG, "Creating channel...");
                channel = connection.acquireChannel();
                LOG.log(Level.DEBUG, "Channel " + channel + " created.");
            } catch (IOException e) {
                LOG.log(Level.DEBUG, "Channel could not be created.", e);
                throw new UncheckedIOException(e);
            } catch (InterruptedException e) {
                LOG.log(Level.DEBUG, "Channel could not be created, because the thread was interrupted.");
                Thread.currentThread().interrupt();
                throw e;
            } catch (RuntimeException e) {
                LOG.log(Level.DEBUG, "Channel could not be created.", e);
                throw e;
            }
            return channel;
        }

        @Override
        public boolean isUsable(Channel channel) {
            return channel.isOpen();
        }

    }

    private static final class ReturnHandler implements ReturnListener {

        private final Event<ReturnedMessage> event;

        private ReturnHandler(Event<ReturnedMessage> event) {this.event = event;}

        @Override
        public void handleReturn(int replyCode, String replyText, String exchange, String routingKey,
                                 AMQP.BasicProperties properties, byte[] body) {
            final Outgoing<byte[]> outgoing = convertToOutgoing(exchange, routingKey, properties, body);
            event.fireAsync(new ReturnedMessage(replyCode, replyText, outgoing));
            // TODO: Logging
        }

        private Outgoing<byte[]> convertToOutgoing(final String exchange, final String routingKey,
                                                   final AMQP.BasicProperties properties, final byte[] body) {
            final DeliveryMode deliveryMode = properties.getDeliveryMode() == 1 ? TRANSIENT : PERSISTENT;

            final MessageBuilder<byte[], ?> messageBuilder;
            if (properties.getReplyTo() != null) {
                messageBuilder = new Outgoing.Request.Builder<>(exchange, routingKey, deliveryMode);
            } else {
                messageBuilder = new Outgoing.Cast.Builder<>(exchange, routingKey, deliveryMode);
            }

            return messageBuilder.setProperties(properties).setType(byte[].class).setContent(body).build();
        }
    }

    private static class ChannelSupplier implements Supplier<Channel>, AutoCloseable {

        private final ChannelLifeCycle channelLifeCycle;
        private final Set<Channel> channels = ConcurrentHashMap.newKeySet();

        private ChannelSupplier(final ChannelLifeCycle lifeCycle) {channelLifeCycle = lifeCycle;}

        @Override
        public Channel get() {
            try {
                final Channel channel = channelLifeCycle.createNew();
                channels.add(channel);
                final ShutdownListener shutdownListener = sse -> channels.remove(channel);
                channel.addShutdownListener(shutdownListener);

                return channel;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }

        @Override
        public void close() {
            channels.forEach(channelLifeCycle::destroy);
        }
    }
}
