package io.github.jhahn.enhancedcdi.messaging.impl;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import io.github.jhahn.enhancedcdi.messaging.Consolidated;
import io.github.jhahn.enhancedcdi.messaging.Topology;
import io.github.jhahnhro.enhancedcdi.pooled.BlockingPool;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
class Infrastructure {

    @Inject
    @Consolidated
    Topology consolidatedTopology;

    @Inject
    BlockingPool<Channel> channelPool;

    public void setUpTopology(Topology topology) throws IOException, InterruptedException {
        channelPool.withItem(channel -> {setUpTopology(topology, channel);});
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
        final Optional<AMQP.Queue.Declare> queueDeclaration = consolidatedTopology.queueDeclarations()
                .stream()
                .filter(qDecl -> qDecl.getQueue().equals(queueName))
                .findAny();
        if (queueDeclaration.isEmpty()) {
            throw new IllegalArgumentException("No declaration for queue \"" + queueName + "\" known");
        }

        final Topology.Builder builder = new Topology.Builder();

        builder.addQueueDeclaration(queueDeclaration.get());

        final Map<String, List<AMQP.Queue.Bind>> bindings = consolidatedTopology.queueBindings()
                .stream()
                .filter(binding -> binding.getQueue().equals(queueName))
                .collect(Collectors.groupingBy(AMQP.Queue.Bind::getExchange));
        bindings.values().forEach(builder::addQueueBindings);

        consolidatedTopology.exchangeDeclarations()
                .stream()
                .filter(exDecl -> bindings.containsKey(exDecl.getExchange()))
                .forEach(builder::addExchangeDeclaration);

        setUpTopology(builder.build());
    }

    public void setUpForExchange(final String exchangeName) throws IOException, InterruptedException {
        final Set<AMQP.Exchange.Declare> exchangeDeclaration = consolidatedTopology.exchangeDeclarations()
                .stream()
                .filter(exDecl -> exDecl.getExchange().equals(exchangeName))
                .collect(Collectors.toSet());

        if (exchangeDeclaration.isEmpty()) {
            throw new IllegalArgumentException("No declaration for exchange \"" + exchangeName + "\" known");
        }

        channelPool.withItem(channel -> {
            setUpExchanges(exchangeDeclaration, channel);
        });
    }
}
