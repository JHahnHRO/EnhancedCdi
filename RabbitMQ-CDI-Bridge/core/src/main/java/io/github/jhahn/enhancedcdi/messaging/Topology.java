package io.github.jhahn.enhancedcdi.messaging;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AMQP.Exchange;
import com.rabbitmq.client.AMQP.Queue;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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
 * A bean of type Topology with qualifier {@link Consolidated @Consolidated} is provided that consolidates any and
 * all beans of types {@link AMQP.Exchange.Declare}, {@link AMQP.Queue.Declare}, {@link AMQP.Queue.Bind}, and
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
        record QueueAndExchange(String queue, String exchange) {}
        final var bindings = groupByName(queueBindings,
                                         bind -> new QueueAndExchange(bind.getQueue(), bind.getExchange()));

        StringBuilder errors = new StringBuilder();
        validateNonConflictingDeclarations(errors, exchangeByName.values());
        validateNonConflictingDeclarations(errors, queueByName.values());
        validateNonConflictingDeclarations(errors, bindings.values());

        for (String name : PREDECLARED_EXCHANGES) {
            if (exchangeByName.containsKey(name)) {
                errors.append("Re-declaration of the built-in exchange \"").append(name).append("\"\n");
            }
        }

        if (queueByName.containsKey(RABBITMQ_REPLY_TO)) {
            errors.append("Declaration of built-in pseudo-queue \"").append(RABBITMQ_REPLY_TO).append("\"\n");
        }

        for (var bind : bindings.keySet()) {
            final String exchange = bind.exchange();
            if (!exchangeByName.containsKey(exchange) && !PREDECLARED_EXCHANGES.contains(exchange)) {
                errors.append("Queue binding for unknown exchange \"").append(exchange).append("\"\n");
            }
            final String queue = bind.queue();
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
