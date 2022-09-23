package io.github.jhahn.enhancedcdi.messaging;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@ApplicationScoped
public class Infrastructure {

    @Inject
    @Consolidated
    Topology consolidatedTopology;

    private final Lock mutex = new ReentrantLock();
    @Inject
    Connection connection;
    private Channel channel = null;

    private Channel getChannel() throws IOException {
        mutex.lock();
        try {
            if (channel == null) {
                channel = connection.createChannel();
                channel.addShutdownListener(cause -> {
                    mutex.lock();
                    this.channel = null;
                    mutex.unlock();
                });
            }

            return channel;
        } finally {
            mutex.unlock();
        }
    }

    public void setUpTopology(Topology topology) throws IOException {
        setUpTopology(topology, getChannel());
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

    public void setUpForQueue(final String queueName) throws IOException {
        final Optional<AMQP.Queue.Declare> queueDeclaration = consolidatedTopology.queueDeclarations()
                .stream()
                .filter(qDecl -> qDecl.getQueue().equals(queueName))
                .findAny();
        if (queueDeclaration.isEmpty()) {
            throw new IllegalArgumentException("No declaration for queue '" + queueName + "' known");
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

        final Topology necessaryTopology = builder.build();

        setUpTopology(necessaryTopology, getChannel());
    }
}
