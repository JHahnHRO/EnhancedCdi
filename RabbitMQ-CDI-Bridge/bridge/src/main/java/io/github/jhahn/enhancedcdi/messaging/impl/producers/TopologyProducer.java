package io.github.jhahn.enhancedcdi.messaging.impl.producers;

import com.rabbitmq.client.AMQP;
import io.github.jhahn.enhancedcdi.messaging.Consolidated;
import io.github.jhahn.enhancedcdi.messaging.Topology;

import javax.annotation.PostConstruct;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class TopologyProducer {

    @Inject
    @Any
    Instance<AMQP.Exchange.Declare> exchanges;
    @Inject
    @Any
    Instance<AMQP.Queue.Declare> queues;
    @Inject
    @Any
    Instance<AMQP.Queue.Bind> bindings;
    @Inject
    @Any
    Instance<Topology> topologies;

    private Topology topology;

    @PostConstruct
    private void createTopology() {
        final Topology.Builder builder = new Topology.Builder();

        exchanges.forEach(builder::addExchangeDeclaration);
        queues.forEach(builder::addQueueDeclaration);
        bindings.forEach(builder::addQueueBinding);
        topologies.forEach(builder::merge);

        this.topology = builder.build();
    }

    @Produces
    @Consolidated
    public Topology getTopology() {
        return topology;
    }
}
