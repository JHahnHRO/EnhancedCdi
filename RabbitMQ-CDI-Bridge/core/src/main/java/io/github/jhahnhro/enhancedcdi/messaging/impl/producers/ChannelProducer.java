package io.github.jhahnhro.enhancedcdi.messaging.impl.producers;

import static io.github.jhahnhro.enhancedcdi.messaging.messages.Message.DeliveryMode.PERSISTENT;
import static io.github.jhahnhro.enhancedcdi.messaging.messages.Message.DeliveryMode.TRANSIENT;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ReturnListener;
import io.github.jhahnhro.enhancedcdi.messaging.impl.Confirmations;
import io.github.jhahnhro.enhancedcdi.messaging.impl.WithConfirms;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Message.DeliveryMode;
import io.github.jhahnhro.enhancedcdi.messaging.messages.MessageBuilder;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;
import io.github.jhahnhro.enhancedcdi.messaging.messages.ReturnedMessage;
import io.github.jhahnhro.enhancedcdi.pooled.BlockingPool;
import io.github.jhahnhro.enhancedcdi.pooled.LazyBlockingPool;

@Singleton
class ChannelProducer {

    private static final System.Logger LOG = System.getLogger(ChannelProducer.class.getCanonicalName());
    private final ReturnListener returnCallback;

    @Inject
    ChannelProducer(Event<ReturnedMessage> event) {
        this.returnCallback = new ReturnHandler(event);
    }

    @Produces
    @Default
    @ApplicationScoped
    BlockingPool<Channel> channelPool(BookkeepingConnection connection) throws InterruptedException {
        LOG.log(Level.DEBUG, "Creating shared pool of channels");
        return new ChannelPool(new ChannelLifeCycle(connection) {
            @Override
            public Channel createNew() throws InterruptedException {
                final Channel channel = super.createNew();
                channel.addReturnListener(returnCallback);
                return channel;
            }
        });
    }

    @Produces
    @WithConfirms
    @ApplicationScoped
    BlockingPool<Channel> channelPoolWithConfirms(BookkeepingConnection connection, Confirmations confirmations)
            throws InterruptedException {
        LOG.log(Level.DEBUG, "Creating shared pool of channels in confirm-mode");
        return new ChannelPool(new ChannelLifeCycle(connection) {
            @Override
            public Channel createNew() throws InterruptedException {
                final Channel channel = super.createNew();
                try {
                    confirmations.putChannelInConfirmMode(channel);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                return channel;
            }
        });
    }

    void dispose(@Disposes @Any BlockingPool<Channel> channelPool) {
        LOG.log(Level.DEBUG, "Shutting down shared pool of channels");
        channelPool.close();
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
            // in case the connection bean has been destroyed and re-created since the last time, we re-size the pool
            // to the new capacity. This is a no-op if the capacity has not changed.
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
                channel.abort();
            } catch (AlreadyClosedException sse) {
                // already shut down
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public Channel createNew() throws InterruptedException {
            final Channel channel;
            try {
                channel = connection.acquireChannel();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
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
}
