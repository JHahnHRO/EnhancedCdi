package io.github.jhahnhro.enhancedcdi.messaging.impl.producers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import io.github.jhahnhro.enhancedcdi.messaging.Consolidated;
import io.github.jhahnhro.enhancedcdi.messaging.Topology;
import io.github.jhahnhro.enhancedcdi.messaging.impl.RuntimeIOException;
import io.github.jhahnhro.enhancedcdi.pooled.BlockingPool;
import io.github.jhahnhro.enhancedcdi.pooled.LazyBlockingPool;

@Singleton
class ChannelProducer {

    private static final System.Logger LOG = System.getLogger(ChannelProducer.class.getCanonicalName());

    @Produces
    @ApplicationScoped
    BlockingPool<Channel> channelPool(Connection connection, @Consolidated Topology consolidatedTopology) {
        LOG.log(Level.DEBUG, "Creating shared pool of channels");
        return new ChannelPool(connection, consolidatedTopology);
    }

    void dispose(@Disposes BlockingPool<Channel> channelPool) {
        LOG.log(Level.DEBUG, "Shutting down shared pool of channels");
        channelPool.close();
    }

    private static class ChannelPool extends LazyBlockingPool<Channel> {
        private final Connection connection;

        public ChannelPool(Connection connection, Topology consolidatedTopology) {
            super(0, computeCapacity(connection, consolidatedTopology), ChannelPool::closeIfNecessary);
            this.connection = connection;
        }

        private static int computeCapacity(Connection connection, Topology consolidatedTopology) {
            // leave room for one consumer channel for each queue. These channels are not taken from the channel pool.
            return connection.getChannelMax() - consolidatedTopology.queueDeclarations().size();
        }

        private static void closeIfNecessary(Channel channel) throws IOException, TimeoutException {
            try {
                channel.close();
            } catch (AlreadyClosedException sse) {
                // already shut down
            }
        }

        @Override
        protected Channel create() {
            Optional<Channel> maybeChannel;
            try {
                maybeChannel = connection.openChannel();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return maybeChannel.orElseThrow(() -> new IllegalStateException("No channels available"));
        }

        @Override
        protected boolean isValid(Channel channel) {
            return channel.isOpen();
        }
    }
}
