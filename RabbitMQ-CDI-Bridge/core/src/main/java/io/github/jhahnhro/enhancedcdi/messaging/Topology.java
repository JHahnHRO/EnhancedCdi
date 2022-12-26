package io.github.jhahnhro.enhancedcdi.messaging;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AMQP.Exchange;
import com.rabbitmq.client.AMQP.Queue;

/**
 * A topology consists of a set of exchange declarations, queue declarations and a set of bindings between (a subset of)
 * those queues and (a subset of) those exchanges.
 * <p>
 * It enforces that
 * <ul>
 *     <li>These three sets and their elements are all non-null</li>
 *     <li>There are no conflicts between exchange declarations, i.e. it cannot contain more than one declaration
 *     with the same name</li>
 *     <li>Pre-defined exchanges cannot be re-declared</li>
 *     <li>There are no conflicts between queue declarations, i.e. it cannot contain more than one declarations with
 *     the same name</li>
 *     <li>Every binding needs to refer to an exchange within the same Topology or a pre-defined exchange and to a
 *     queue within the same topology.</li>
 * </ul>
 * <p>
 * A bean of type {@code Topology} with qualifier {@link Consolidated @Consolidated} is provided that consolidates
 * any and all beans of types {@link AMQP.Exchange.Declare}, {@link AMQP.Queue.Declare}, {@link AMQP.Queue.Bind}, and
 * {@link Topology} into one single Topology.
 */
public record Topology(Set<Exchange.Declare> exchangeDeclarations, Set<Queue.Declare> queueDeclarations,
                       Set<Queue.Bind> queueBindings) {

    public static final Set<String> PREDECLARED_EXCHANGES = Set.of("", "amq.direct", "amq.fanout", "amq.topic",
                                                                   "amq.match", "amq.headers");
    public static final String RABBITMQ_REPLY_TO = "amq.rabbitmq.reply-to";

    public Topology {
        // throws on null values and null elements
        exchangeDeclarations = Set.copyOf(exchangeDeclarations);
        queueDeclarations = Set.copyOf(queueDeclarations);
        queueBindings = Set.copyOf(queueBindings);

        final var exchangeByName = groupByName(exchangeDeclarations, Exchange.Declare::getExchange);
        final var queueByName = groupByName(queueDeclarations, Queue.Declare::getQueue);

        StringBuilder errors = new StringBuilder();
        validateNonConflictingDeclarations(errors, exchangeByName.values());
        validateNonConflictingDeclarations(errors, queueByName.values());

        for (String name : PREDECLARED_EXCHANGES) {
            if (exchangeByName.containsKey(name)) {
                errors.append("Re-declaration of the built-in exchange \"").append(name).append("\"\n");
            }
        }

        if (queueByName.containsKey(RABBITMQ_REPLY_TO)) {
            errors.append("Declaration of built-in pseudo-queue \"").append(RABBITMQ_REPLY_TO).append("\"\n");
        }

        for (var bind : queueBindings) {
            final String exchange = bind.getExchange();
            if (!exchangeByName.containsKey(exchange) && !PREDECLARED_EXCHANGES.contains(exchange)) {
                errors.append("Queue binding for unknown exchange \"").append(exchange).append("\"\n");
            }
            final String queue = bind.getQueue();
            if (queue.equals(RABBITMQ_REPLY_TO)) {
                errors.append("Built-in pseudo-queue \"")
                        .append(RABBITMQ_REPLY_TO)
                        .append("\" cannot be bound to any exchange");
            } else if (!queueByName.containsKey(queue)) {
                errors.append("Queue binding for unknown queue \"").append(queue).append("\"\n");
            }
        }

        if (!errors.isEmpty()) {
            throw new IllegalDeclarationException(errors.toString());
        }
    }

    private <T> void validateNonConflictingDeclarations(StringBuilder errors, Collection<List<T>> values) {
        values.stream()
                .filter(declarations -> declarations.size() > 1)
                .map(declarations -> "Multiple conflicting declarations: " + declarations + '\n')
                .forEach(errors::append);
    }

    private <K, T> Map<K, List<T>> groupByName(Collection<T> values, Function<T, K> keyMapper) {
        return values.stream().collect(Collectors.groupingBy(keyMapper));
    }

    /**
     * Returns a sub-topology of this topology, i.e. a topology consisting of only the given queue (if the given queue
     * is part of this topology), the bindings of this topology referring to that queue, and the exchanges of this
     * topology necessary for those bindings. If the given queue is not contained in this topology, the return topology
     * will be empty.
     *
     * @param queueName the name of a queue
     * @return a topology consisting of only the given queue, bindings for that queue, and exchanges for those bindings
     * @throws NullPointerException if {@code queueName} is null
     */
    public Topology subTopologyForQueue(String queueName) {
        return subTopologyForQueues(Set.of(queueName));
    }

    /**
     * Returns a sub-topology of this topology consisting of:
     * <ul>
     *     <li>the queues contained in this topology whose names are elements of the given set of names,</li>
     *     <li>the bindings contained in this topology referring to those queues, and</li>
     *     <li>the exchanges contained in this topology necessary for those bindings.</li>
     * </ul>
     * In particular: If the set is empty or contains only queues unknown to this topology, the returned topology
     * will be empty.
     *
     * @param queueNames a set of queue names
     * @return a sub-topology consisting of only the given queues, their bindings, and exchanges for those bindings.
     * @throws NullPointerException if the given set of names is null.
     */
    public Topology subTopologyForQueues(Set<String> queueNames) {
        final Builder builder = new Builder();

        final Set<Queue.Declare> queueDeclarations = queueDeclarations().stream()
                .filter(qDecl -> queueNames.contains(qDecl.getQueue()))
                .collect(Collectors.toSet());

        builder.addQueueDeclarations(queueDeclarations);

        final Map<String, List<Queue.Bind>> bindings = queueBindings().stream()
                .filter(binding -> queueNames.contains(binding.getQueue()))
                .collect(Collectors.groupingBy(Queue.Bind::getExchange));

        bindings.values().forEach(builder::addQueueBindings);

        exchangeDeclarations().stream()
                .filter(exDecl -> bindings.containsKey(exDecl.getExchange()))
                .forEach(builder::addExchangeDeclaration);

        return builder.build();
    }

    public static class Builder {
        private final Set<AMQP.Exchange.Declare> exchangeDeclarations = new HashSet<>();
        private final Set<AMQP.Queue.Declare> queueDeclarations = new HashSet<>();
        private final Set<AMQP.Queue.Bind> queueBindings = new HashSet<>();

        public Builder merge(Topology topology) {
            return addExchangeDeclarations(topology.exchangeDeclarations).addQueueDeclarations(
                    topology.queueDeclarations).addQueueBindings(topology.queueBindings);
        }

        public Builder setExchangeDeclarations(Collection<AMQP.Exchange.Declare> exchangeDeclarations) {
            this.exchangeDeclarations.clear();
            return this.addExchangeDeclarations(exchangeDeclarations);
        }

        public Builder addExchangeDeclarations(Collection<AMQP.Exchange.Declare> exchangeDeclarations) {
            this.exchangeDeclarations.addAll(exchangeDeclarations);
            return this;
        }

        public Builder addExchangeDeclaration(AMQP.Exchange.Declare exchangeDeclaration) {
            this.exchangeDeclarations.add(exchangeDeclaration);
            return this;
        }

        public Builder setQueueDeclarations(Collection<AMQP.Queue.Declare> queueDeclarations) {
            this.queueDeclarations.clear();
            return this.addQueueDeclarations(queueDeclarations);
        }

        public Builder addQueueDeclarations(Collection<AMQP.Queue.Declare> queueDeclarations) {
            this.queueDeclarations.addAll(queueDeclarations);
            return this;
        }

        public Builder addQueueDeclaration(AMQP.Queue.Declare queueDeclaration) {
            this.queueDeclarations.add(queueDeclaration);
            return this;
        }

        public Builder setQueueBindings(Collection<AMQP.Queue.Bind> queueBindings) {
            this.queueBindings.clear();
            return this.addQueueBindings(queueBindings);
        }

        public Builder addQueueBinding(AMQP.Queue.Bind queueBinding) {
            this.queueBindings.add(queueBinding);
            return this;
        }

        public Builder addQueueBindings(Collection<AMQP.Queue.Bind> queueBindings) {
            this.queueBindings.addAll(queueBindings);
            return this;
        }

        public Topology build() {
            return new Topology(exchangeDeclarations, queueDeclarations, queueBindings);
        }
    }

    public static class IllegalDeclarationException extends IllegalArgumentException {

        public IllegalDeclarationException(String message) {
            super(message);
        }
    }
}
