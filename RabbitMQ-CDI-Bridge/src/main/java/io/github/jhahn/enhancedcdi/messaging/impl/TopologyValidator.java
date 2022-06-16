package io.github.jhahn.enhancedcdi.messaging.impl;

import com.rabbitmq.client.AMQP;
import io.github.jhahn.enhancedcdi.messaging.Topology;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.DefinitionException;
import javax.inject.Inject;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class TopologyValidator {

    @Inject
    @Any
    private Instance<AMQP.Exchange.Declare> exchanges;
    @Inject
    @Any
    private Instance<AMQP.Queue.Declare> queues;
    @Inject
    @Any
    private Instance<AMQP.Queue.Bind> bindings;
    @Inject
    @Any
    private Instance<Topology> topologies;

    @Inject
    RabbitMqExtension rabbitMqExtension;

    private Topology topology;

    @PostConstruct
    private void createTopology() {
        final Topology.Builder builder = new Topology.Builder();

        exchanges.forEach(builder::addExchangeDeclaration);
        queues.forEach(builder::addQueueDeclaration);
        bindings.forEach(builder::addQueueBinding);
        topologies.forEach(builder::merge);

        this.topology = builder.build();

        validateTopology();
    }

    private void validateTopology() {
        final Set<String> declaredQueueNames = this.topology.queueDeclarations()
                .stream()
                .map(AMQP.Queue.Declare::getQueue)
                .collect(Collectors.toSet());

        final Set<String> declaredExchangeNames = this.topology.exchangeDeclarations()
                .stream()
                .map(AMQP.Exchange.Declare::getExchange)
                .collect(Collectors.toCollection(HashSet::new));
        declaredExchangeNames.addAll(Topology.PREDECLARED_EXCHANGES);


        StringBuilder error = new StringBuilder();
        rabbitMqExtension.getNecessaryQueues()
                .stream()
                .filter(necessaryName -> !declaredQueueNames.contains(necessaryName.name()))
                .forEach(necessaryName -> error.append("The method(s) ")
                        .append(necessaryName.causes())
                        .append(" require(s) the queue '")
                        .append(necessaryName.name())
                        .append("', but no declaration for a queue of that name was found.\n"));

        rabbitMqExtension.getNecessaryExchanges()
                .stream()
                .filter(necessaryName -> !declaredExchangeNames.contains(necessaryName.name()))
                .forEach(necessaryName -> error.append("The method(s) ")
                        .append(necessaryName.causes())
                        .append(" require(s) the exchange '")
                        .append(necessaryName.name())
                        .append("', but no declaration for an exchange of that name was found.\n"));

        if (!error.isEmpty()) {
            throw new DefinitionException(error.toString());
        }
    }

    public Topology getTopology() {
        return topology;
    }
}
