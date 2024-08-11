package io.github.jhahnhro.enhancedcdi.messaging.impl.producers;

import java.util.stream.Stream;

import com.rabbitmq.client.AMQP;
import io.github.jhahnhro.enhancedcdi.messaging.Consolidated;
import io.github.jhahnhro.enhancedcdi.messaging.Topology;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.TransientReference;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
class TopologyProducer {

    @Produces
    @Singleton
    @Consolidated
    Topology topology;

    @Inject
    TopologyProducer(@TransientReference @Any Instance<AMQP.Exchange.Declare> exchanges,
                     @TransientReference @Any Instance<AMQP.Queue.Declare> queues,
                     @TransientReference @Any Instance<AMQP.Queue.Bind> bindings, BeanManager bm) {

        final Topology.Builder builder = new Topology.Builder();

        exchanges.forEach(builder::addExchangeDeclaration);
        queues.forEach(builder::addQueueDeclaration);
        bindings.forEach(builder::addQueueBinding);

        getOtherTopologies(bm).forEach(builder::merge);

        this.topology = builder.build();
    }

    private Stream<Topology> getOtherTopologies(BeanManager bm) {
        return bm.getBeans(Topology.class, Any.Literal.INSTANCE).stream()
                // we need to filter beans here, because one of the beans of type Topology is the one with qualifier
                // @Consolidated that is the union of all others that is being constructed right now.
                .filter(bean -> bean.getBeanClass() != TopologyProducer.class).map(bean -> {
                    final CreationalContext<?> creationalContext = bm.createCreationalContext(bean);
                    return (Topology) bm.getReference(bean, Topology.class, creationalContext);
                });
    }
}
