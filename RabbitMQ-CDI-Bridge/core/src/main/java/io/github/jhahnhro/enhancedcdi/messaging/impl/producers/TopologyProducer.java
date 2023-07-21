package io.github.jhahnhro.enhancedcdi.messaging.impl.producers;

import java.util.List;
import javax.annotation.PostConstruct;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.BeanManager;
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
    List<Topology> otherTopologies;
    @Produces
    @Singleton
    @Consolidated
    private Topology topology;

    @Inject
    void setOtherTopologies(BeanManager bm) {
        // we need to filter beans here, because one of the beans of type Topology is the one with qualifier
        // @Consolidated that is the union of all others.
        this.otherTopologies = bm.getBeans(Topology.class, Any.Literal.INSTANCE)
                .stream()
                .filter(bean -> !bean.getQualifiers().contains(Consolidated.Literal.INSTANCE))
                .map(bean -> {
                    final CreationalContext<?> creationalContext = bm.createCreationalContext(bean);
                    return (Topology) bm.getReference(bean, Topology.class, creationalContext);
                })
                .toList();
    }

    @PostConstruct
    private void createTopology() {
        final Topology.Builder builder = new Topology.Builder();

        exchanges.forEach(builder::addExchangeDeclaration);
        queues.forEach(builder::addQueueDeclaration);
        bindings.forEach(builder::addQueueBinding);
        otherTopologies.forEach(builder::merge);

        this.topology = builder.build();
    }
}
