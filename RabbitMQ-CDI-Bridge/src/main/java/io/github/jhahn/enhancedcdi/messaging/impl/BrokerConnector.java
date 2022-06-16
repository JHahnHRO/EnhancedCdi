package io.github.jhahn.enhancedcdi.messaging.impl;

import com.rabbitmq.client.*;
import io.github.jhahn.enhancedcdi.messaging.Topology;
import org.eclipse.microprofile.config.Config;

import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

@ApplicationScoped
public class BrokerConnector {

    @Inject
    TopologyValidator topologyValidator;
    @Inject
    EventBridge eventBridge;

    private ConnectionFactory connectionFactory;
    private Connection connection;
    private final Map<String, Channel> consumerChannels = new ConcurrentHashMap<>();

    @Inject
    private void initConnectionFactory(Config applicationConfig) {
        Map<String, String> config = new HashMap<>();

        applicationConfig.getPropertyNames()
                .forEach(name -> applicationConfig.getOptionalValue(name, String.class)
                        .ifPresent(value -> config.put(name, value)));

        this.connectionFactory = new ConnectionFactory();
        ConnectionFactoryConfigurator.load(connectionFactory, config);
    }

    private void connect() throws IOException, TimeoutException {
        this.connection = connectionFactory.newConnection();
        declareTopology();
        declareConsumers();
    }

    private void declareConsumers() throws IOException {

        for (AMQP.Queue.Declare declare : topologyValidator.getTopology().queueDeclarations()) {
            final String queueName = declare.getQueue();
            final Channel channel = connection.createChannel();

            final String consumerTag = channel.basicConsume(queueName, true,
                                                            eventBridge.createDeliveryCallback(queueName), tag -> {});
            consumerChannels.put(consumerTag, channel);
        }
    }

    private void declareTopology() throws IOException, TimeoutException {
        final Topology topology = topologyValidator.getTopology();
        try (Channel topologyChannel = this.connection.createChannel()) {
            for (AMQP.Exchange.Declare d : topology.exchangeDeclarations()) {
                topologyChannel.exchangeDeclare(d.getExchange(), d.getType(), d.getDurable(), d.getAutoDelete(),
                                                d.getArguments());
            }
            for (AMQP.Queue.Declare d : topology.queueDeclarations()) {
                topologyChannel.queueDeclare(d.getQueue(), d.getDurable(), d.getExclusive(), d.getAutoDelete(),
                                             d.getArguments());
            }
            for (AMQP.Queue.Bind d : topology.queueBindings()) {
                topologyChannel.queueBind(d.getQueue(), d.getExchange(), d.getRoutingKey(), d.getArguments());
            }
        }
    }

    public void fireAndForget(String exchange, String routingKey, AMQP.BasicProperties properties, byte[] body)
            throws IOException, TimeoutException {
        try (Channel channel = connection.createChannel()) {
            channel.basicPublish(exchange, routingKey, properties, body);
        }
    }

    public RpcClient.Response doRpc(String exchange, String routingKey, AMQP.BasicProperties properties, byte[] body)
            throws IOException, TimeoutException {
        try (Channel channel = connection.createChannel()) {
            final RpcClientParams rpcClientParams = new RpcClientParams().channel(channel)
                    .exchange(exchange)
                    .routingKey(routingKey);
            final RpcClient rpcClient = new RpcClient(rpcClientParams);
            return rpcClient.doCall(properties, body);
        }
    }

    @Produces
    @ApplicationScoped
    Connection produceConnection() throws IOException, TimeoutException {
        if (connection == null || !connection.isOpen()) {
            connect();
        }
        return connection;
    }

    @PreDestroy
    void closeConnection() throws IOException, TimeoutException {
        closeChannelsAndConnection(this.connection);
    }

    void disposeConnection(@Disposes Connection connection) throws IOException, TimeoutException {
        closeChannelsAndConnection(connection);
    }

    private void closeChannelsAndConnection(Connection connection) throws IOException, TimeoutException {
        for (Map.Entry<String, Channel> entry : consumerChannels.entrySet()) {
            String consumerTag = entry.getKey();
            Channel channel = entry.getValue();
            channel.basicCancel(consumerTag);
            channel.close();
        }

        if (connection != null) {
            this.connection.close();
            this.connection = null;
        }
    }
}
