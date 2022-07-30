package io.github.jhahn.enhancedcdi.messaging.impl;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.RpcClient;
import com.rabbitmq.client.RpcClientParams;
import io.github.jhahnhro.enhancedcdi.pooled.Pool;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

@ApplicationScoped
class LowLevelPublisher {

    @Inject
    private Pool<Channel> publisherChannels;

    public void doBroadcast(String exchange, String routingKey, AMQP.BasicProperties properties, byte[] body)
            throws IOException, InterruptedException {
        publisherChannels.withItem(channel -> {
            channel.basicPublish(exchange, routingKey, properties, body);
            return null;
        });
    }

    public RpcClient.Response doRpc(String exchange, String routingKey, AMQP.BasicProperties properties, byte[] body)
            throws IOException, InterruptedException {
        return publisherChannels.withItem(channel -> {
            final RpcClientParams rpcClientParams = new RpcClientParams().channel(channel)
                    .exchange(exchange)
                    .routingKey(routingKey)
                    .correlationIdSupplier(() -> UUID.randomUUID().toString());
            final RpcClient rpcClient = new RpcClient(rpcClientParams);

            try {
                return rpcClient.doCall(properties, body);
            } catch (TimeoutException timeoutException) {
                throw new RuntimeException(timeoutException);
            }
        });
    }
}
