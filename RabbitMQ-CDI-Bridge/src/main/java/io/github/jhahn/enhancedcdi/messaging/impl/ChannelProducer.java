package io.github.jhahn.enhancedcdi.messaging.impl;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import io.github.jhahnhro.enhancedcdi.pooled.Pool;
import io.github.jhahnhro.enhancedcdi.pooled.PoolImpl;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Produces;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

@ApplicationScoped
class ChannelProducer {

    @Produces
    @Dependent
    Channel channel(Connection connection) throws IOException {
        return connection.createChannel();
    }

    void dispose(@Disposes Channel channel) throws IOException, TimeoutException {
        channel.close();
    }

    @Produces
    @ApplicationScoped
    Pool<Channel> channelPool(Instance<Channel> channelInstance) {
        return new PoolImpl<>(0, 20, channelInstance::get, channelInstance::destroy);
    }

    void dispose(@Disposes Pool<Channel> channelPool) {
        channelPool.close();
    }
}
