package io.github.jhahn.enhancedcdi.messaging;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.util.Set;

@ApplicationScoped
public class Infrastructure {

    @Inject
    @Consolidated
    Topology consolidatedTopology;

    public void setUpTopology(Topology topology, Channel channel) throws IOException {
        setUpExchanges(topology.exchangeDeclarations(), channel);
        setUpQueues(topology.queueDeclarations(), channel);
        bindQueuesToExchanges(topology.queueBindings(), channel);
    }

    public void setUpExchanges(final Set<AMQP.Exchange.Declare> exchangeDeclarations, Channel channel)
            throws IOException {
        for (AMQP.Exchange.Declare d : exchangeDeclarations) {
            setUpExchange(d, channel);
        }
    }

    public void setUpExchange(AMQP.Exchange.Declare d, Channel channel) throws IOException {
        channel.exchangeDeclare(d.getExchange(), d.getType(), d.getDurable(), d.getAutoDelete(), d.getArguments());
    }

    public void setUpQueues(final Set<AMQP.Queue.Declare> queueDeclarations, Channel channel) throws IOException {
        for (AMQP.Queue.Declare d : queueDeclarations) {
            setUpQueue(d, channel);
        }
    }

    public void setUpQueue(AMQP.Queue.Declare d, Channel channel) throws IOException {
        channel.queueDeclare(d.getQueue(), d.getDurable(), d.getExclusive(), d.getAutoDelete(), d.getArguments());
    }

    public void bindQueuesToExchanges(final Set<AMQP.Queue.Bind> queueBindings, Channel channel) throws IOException {
        for (AMQP.Queue.Bind d : queueBindings) {
            bind(d, channel);
        }
    }

    public void bind(AMQP.Queue.Bind d, Channel channel) throws IOException {
        channel.queueBind(d.getQueue(), d.getExchange(), d.getRoutingKey(), d.getArguments());
    }
}
