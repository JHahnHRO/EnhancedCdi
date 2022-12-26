package io.github.jhahnhro.enhancedcdi.messaging.impl.producers;

import java.util.Objects;
import javax.annotation.PostConstruct;
import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.rabbitmq.client.AMQP;
import io.github.jhahnhro.enhancedcdi.messaging.Consolidated;
import io.github.jhahnhro.enhancedcdi.messaging.Topology;

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

    private Topology topology = null; // null is a temporary value

    @PostConstruct
    private void createTopology() {
        final Topology.Builder builder = new Topology.Builder();

        exchanges.forEach(builder::addExchangeDeclaration);
        queues.forEach(builder::addQueueDeclaration);
        bindings.forEach(builder::addQueueBinding);
        // we need to filter out null, because one of the beans of type Topology is the one with qualifier
        // @Consolidated that we're constructing right now. At the moment, that bean is null.
        topologies.stream().filter(Objects::nonNull).forEach(builder::merge);

        this.topology = builder.build();
    }

    @Produces
    @Dependent // needs to be @Dependent, because we need to have null as a temporary value
    @Consolidated
    public Topology getTopology() {
        return topology;
    }
}
