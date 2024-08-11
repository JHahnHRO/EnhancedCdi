package io.github.jhahnhro.enhancedcdi.messaging.impl;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import io.github.jhahnhro.enhancedcdi.messaging.Consolidated;
import io.github.jhahnhro.enhancedcdi.messaging.Topology;
import io.github.jhahnhro.enhancedcdi.pooled.BlockingPool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
class Infrastructure {

    @Inject
    @Consolidated
    Topology consolidatedTopology;

    @Inject
    BlockingPool<Channel> channelPool;

    public void setUpTopology(Topology topology) throws IOException, InterruptedException {
        channelPool.run(channel -> setUpTopology(topology, channel));
    }

    private void setUpTopology(Topology topology, Channel channel) throws IOException {
        setUpExchanges(topology.exchangeDeclarations(), channel);
        setUpQueues(topology.queueDeclarations(), channel);
        bindQueuesToExchanges(topology.queueBindings(), channel);
    }

    private void setUpExchanges(final Set<AMQP.Exchange.Declare> exchangeDeclarations, Channel channel)
            throws IOException {
        for (AMQP.Exchange.Declare d : exchangeDeclarations) {
            channel.exchangeDeclare(d.getExchange(), d.getType(), d.getDurable(), d.getAutoDelete(), d.getArguments());
        }
    }

    private void setUpQueues(final Set<AMQP.Queue.Declare> queueDeclarations, Channel channel) throws IOException {
        for (AMQP.Queue.Declare d : queueDeclarations) {
            channel.queueDeclare(d.getQueue(), d.getDurable(), d.getExclusive(), d.getAutoDelete(), d.getArguments());
        }
    }

    private void bindQueuesToExchanges(final Set<AMQP.Queue.Bind> queueBindings, Channel channel) throws IOException {
        for (AMQP.Queue.Bind d : queueBindings) {
            channel.queueBind(d.getQueue(), d.getExchange(), d.getRoutingKey(), d.getArguments());
        }
    }

    public void setUpForQueue(final String queueName) throws IOException, InterruptedException {
        channelPool.run(channel -> setUpForQueue(queueName, channel));
    }

    void setUpForQueue(final String queueName, Channel channel) throws IOException {
        if (consolidatedTopology.queueDeclarations().stream().noneMatch(q -> q.getQueue().equals(queueName))) {
            throw new IllegalArgumentException("No declaration for queue \"" + queueName + "\" known");
        }
        setUpTopology(consolidatedTopology.subTopologyForQueue(queueName), channel);
    }

    public void setUpForExchange(final String exchangeName) throws IOException, InterruptedException {
        final Set<AMQP.Exchange.Declare> exchangeDeclaration = consolidatedTopology.exchangeDeclarations()
                .stream()
                .filter(exDecl -> exDecl.getExchange().equals(exchangeName))
                .collect(Collectors.toSet());

        if (exchangeDeclaration.isEmpty()) {
            throw new IllegalArgumentException("No declaration for exchange \"" + exchangeName + "\" known");
        }

        channelPool.run(channel -> setUpExchanges(exchangeDeclaration, channel));
    }
}
