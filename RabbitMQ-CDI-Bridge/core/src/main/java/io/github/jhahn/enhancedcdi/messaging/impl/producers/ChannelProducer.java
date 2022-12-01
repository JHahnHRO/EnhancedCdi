package io.github.jhahn.enhancedcdi.messaging.impl.producers;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ShutdownSignalException;
import io.github.jhahn.enhancedcdi.messaging.impl.RuntimeIOException;
import io.github.jhahnhro.enhancedcdi.pooled.BlockingPool;
import io.github.jhahnhro.enhancedcdi.pooled.LazyBlockingPool;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.util.concurrent.TimeoutException;

@Singleton
class ChannelProducer {

    private static final System.Logger LOG = System.getLogger(ChannelProducer.class.getCanonicalName());

    @Produces
    @ApplicationScoped
    BlockingPool<Channel> channelPool(Connection connection) {
        LOG.log(Level.DEBUG, "Creating shared pool of channels");
        return new ChannelPool(connection);
    }

    void dispose(@Disposes BlockingPool<Channel> channelPool) {
        LOG.log(Level.DEBUG, "Shutting down shared pool of channels");
        channelPool.close();
    }

    private static class ChannelPool extends LazyBlockingPool<Channel> {
        private final Connection connection;

        public ChannelPool(Connection connection) {
            super(0, connection.getChannelMax(), ChannelPool::closeIfNecessary);
            this.connection = connection;
        }

        private static void closeIfNecessary(Channel channel) throws IOException, TimeoutException {
            try {
                channel.close();
            } catch (ShutdownSignalException sse) {
                // already shut down
            }
        }

        @Override
        protected Channel create() {
            Channel channel = createChannel();
            if (channel == null) {
                throw new IllegalStateException("No channels available");
            }
            return channel;
        }

        private Channel createChannel() {
            try {
                return connection.createChannel();
            } catch (IOException e) {
                throw new RuntimeIOException(e);
            }
        }

        @Override
        protected boolean isValid(Channel channel) {
            return channel.isOpen();
        }
    }
}
