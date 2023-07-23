package io.github.jhahnhro.enhancedcdi.messaging.impl.producers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import io.github.jhahnhro.enhancedcdi.messaging.Consolidated;
import io.github.jhahnhro.enhancedcdi.messaging.Topology;
import io.github.jhahnhro.enhancedcdi.pooled.BlockingPool;
import io.github.jhahnhro.enhancedcdi.pooled.LazyBlockingPool;

@Singleton
class ChannelProducer {

    private static final System.Logger LOG = System.getLogger(ChannelProducer.class.getCanonicalName());

    @Produces
    @ApplicationScoped
    BlockingPool<Channel> channelPool(Connection connection, @Consolidated Topology consolidatedTopology) {
        LOG.log(Level.DEBUG, "Creating shared pool of channels");
        return new LazyBlockingPool<>(0, computeCapacity(connection, consolidatedTopology),
                                      new ChannelLifeCycle(connection));
    }

    void dispose(@Disposes BlockingPool<Channel> channelPool) {
        LOG.log(Level.DEBUG, "Shutting down shared pool of channels");
        channelPool.close();
    }

    private int computeCapacity(Connection connection, Topology consolidatedTopology) {
        // leave room for one consumer channel for each queue. These channels are not taken from the channel pool.
        return connection.getChannelMax() - consolidatedTopology.queueDeclarations().size();
    }

    private static class ChannelLifeCycle implements LazyBlockingPool.Lifecycle<Channel> {
        private final Connection connection;

        public ChannelLifeCycle(Connection connection) {
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
        public Channel createNew() {
            final Channel channel;
            try {
                channel = connection.openChannel()
                        .orElseThrow(() -> new IllegalStateException("No channels available"));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return channel;
        }

        @Override
        public boolean isUseable(Channel channel) {
            return channel.isOpen();
        }
    }
}
