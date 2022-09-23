package io.github.jhahn.enhancedcdi.messaging.impl;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import io.github.jhahnhro.enhancedcdi.pooled.BlockingPool;
import io.github.jhahnhro.enhancedcdi.pooled.LazyBlockingPool;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;
import java.io.IOException;
import java.lang.System.Logger.Level;

@ApplicationScoped
class ChannelProducer {

    private static final System.Logger LOG = System.getLogger(ChannelProducer.class.getCanonicalName());

    @Produces
    @ApplicationScoped
    BlockingPool<Channel> channelPool(Connection connection) {
        LOG.log(Level.DEBUG, "Creating shared pool of channels");
        return new ChannelPool(connection);
    }

    void dispose(@Disposes BlockingPool<Channel> channelPool) {
        LOG.log(Level.INFO, "Shutting down shared pool of channels");
        channelPool.close();
    }

    private static class ChannelPool extends LazyBlockingPool<Channel> {
        private final Connection connection;

        public ChannelPool(Connection connection) {
            super(0, 20, Channel::close);
            this.connection = connection;
            this.connection.addShutdownListener(shutdownSignal -> close());
        }

        @Override
        protected Channel create() {
            Channel channel = createChannel();
            if (channel == null) {
                throw new IllegalStateException("No channels available");
            }
            channel.addShutdownListener(shutdownSignal -> removeUnusableItem(channel));
            return channel;
        }

        private Channel createChannel() {
            try {
                return connection.createChannel();
            } catch (IOException e) {
                throw new RuntimeIOException(e);
            }
        }
    }
}
