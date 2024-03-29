package io.github.jhahnhro.enhancedcdi.messaging.impl.producers;

import static io.github.jhahnhro.enhancedcdi.messaging.messages.Message.DeliveryMode.PERSISTENT;
import static io.github.jhahnhro.enhancedcdi.messaging.messages.Message.DeliveryMode.TRANSIENT;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Return;
import com.rabbitmq.client.ReturnCallback;
import io.github.jhahnhro.enhancedcdi.messaging.Consolidated;
import io.github.jhahnhro.enhancedcdi.messaging.Topology;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Message.DeliveryMode;
import io.github.jhahnhro.enhancedcdi.messaging.messages.MessageBuilder;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;
import io.github.jhahnhro.enhancedcdi.messaging.messages.ReturnedMessage;
import io.github.jhahnhro.enhancedcdi.pooled.BlockingPool;
import io.github.jhahnhro.enhancedcdi.pooled.LazyBlockingPool;

@Singleton
class ChannelProducer {

    private static final System.Logger LOG = System.getLogger(ChannelProducer.class.getCanonicalName());
    private final ReturnCallback returnCallback;

    @Inject
    ChannelProducer(Event<ReturnedMessage> event) {
        this.returnCallback = new ReturnHandler(event);
    }


    @Produces
    @ApplicationScoped
    BlockingPool<Channel> channelPool(Connection connection, @Consolidated Topology consolidatedTopology) {
        LOG.log(Level.DEBUG, "Creating shared pool of channels");
        return new LazyBlockingPool<>(0, computeCapacity(connection, consolidatedTopology),
                                      new ChannelLifeCycle(connection, returnCallback));
    }

    void dispose(@Disposes BlockingPool<Channel> channelPool) {
        LOG.log(Level.DEBUG, "Shutting down shared pool of channels");
        channelPool.close();
    }

    private int computeCapacity(Connection connection, Topology consolidatedTopology) {
        // leave room for one consumer channel for each queue. These channels are not taken from the channel pool.
        return connection.getChannelMax() - consolidatedTopology.queueDeclarations().size();
    }

    private static final class ChannelLifeCycle implements LazyBlockingPool.Lifecycle<Channel> {
        private final Connection connection;
        private final ReturnCallback returnCallback;

        public ChannelLifeCycle(Connection connection, ReturnCallback returnCallback) {
            this.connection = connection;
            this.returnCallback = returnCallback;
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
        public Channel createNew() {
            final Channel channel;
            try {
                channel = connection.openChannel()
                        .orElseThrow(() -> new IllegalStateException("No channels available"));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            channel.addReturnListener(this.returnCallback);
            return channel;
        }

        @Override
        public boolean isUsable(Channel channel) {
            return channel.isOpen();
        }

    }

    private static final class ReturnHandler implements ReturnCallback {
        private final Event<ReturnedMessage> event;

        private ReturnHandler(Event<ReturnedMessage> event) {this.event = event;}

        @Override
        public void handle(Return returnedMsg) {
            event.fireAsync(new ReturnedMessage(returnedMsg.getReplyCode(), returnedMsg.getReplyText(),
                                                convertToOutgoing(returnedMsg)));
        }

        private Outgoing<byte[]> convertToOutgoing(Return returnedMsg) {
            final AMQP.BasicProperties properties = returnedMsg.getProperties();
            final String exchange = returnedMsg.getExchange();
            final String routingKey = returnedMsg.getRoutingKey();
            final DeliveryMode deliveryMode = properties.getDeliveryMode() == 1 ? TRANSIENT : PERSISTENT;

            final MessageBuilder<byte[], ?> messageBuilder;
            if (properties.getReplyTo() != null) {
                messageBuilder = new Outgoing.Request.Builder<>(exchange, routingKey, deliveryMode);
            } else {
                messageBuilder = new Outgoing.Cast.Builder<>(exchange, routingKey, deliveryMode);
            }

            return messageBuilder.setProperties(properties)
                    .setType(byte[].class)
                    .setContent(returnedMsg.getBody())
                    .build();
        }
    }
}
