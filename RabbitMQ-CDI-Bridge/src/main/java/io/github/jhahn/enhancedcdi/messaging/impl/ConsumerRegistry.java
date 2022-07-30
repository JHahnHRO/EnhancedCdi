package io.github.jhahn.enhancedcdi.messaging.impl;

import com.rabbitmq.client.*;
import io.github.jhahn.enhancedcdi.messaging.*;

import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.EventMetadata;
import javax.inject.Inject;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@ApplicationScoped
public class ConsumerRegistry {
    private static final Logger EVENT_BRIDGE_LOG = Logger.getLogger(EventBridge.class.getCanonicalName());

    /**
     * Map queue names => Consumers (Because we only dispatch message to the CDI event system, there only ever needs to
     * be one consumer per queue)
     * <p>
     * TODO: use ConcurrentMap, AtomicBoolean, remove lock
     */
    private final Map<String, DispatchingConsumer> consumers = new HashMap<>();
    private final Lock lock = new ReentrantLock();
    private final Collection<CompletionStage<Delivery>> runningEvents = ConcurrentHashMap.newKeySet();
    @Inject
    @Incoming
    Event<Delivery> dispatcher;
    @Inject
    Instance<Channel> channelInstance;
    @Inject
    @Consolidated
    Topology topology;
    @Inject
    Infrastructure infrastructure;
    private boolean destroyed = false;

    public void startReceiving(@Observes StartReceiving startReceiving, EventMetadata metadata)
            throws IOException, TimeoutException {
        final Optional<String> queueName = metadata.getQualifiers()
                .stream()
                .filter(q -> q.annotationType() == FromQueue.class)
                .map(FromQueue.class::cast)
                .map(FromQueue::value)
                .findAny();

        if (queueName.isEmpty()) {
            throw new IllegalArgumentException("StartReceiving event sent without @FromQueue qualifier");
        }

        final String qName = queueName.get();

        try {
            lock.lock();
            if (destroyed) {
                throw new IllegalStateException(
                        "Cannot start receiving from queue, because the ConsumerRegistry is being destroyed.");
            }
            final Consumer existingConsumer = consumers.get(qName);
            if (existingConsumer == null) {
                final DispatchingConsumer consumer = createConsumer(qName);
                final Channel channel = consumer.getChannel();
                setUpNecessaryInfrastructure(qName, channel);
                consumers.put(qName, consumer);
                channel.basicConsume(qName, consumer);
            }
        } finally {
            lock.unlock();
        }
    }

    private void setUpNecessaryInfrastructure(String qName, Channel channel) throws IOException, TimeoutException {
        final Optional<AMQP.Queue.Declare> queueDeclaration = topology.queueDeclarations()
                .stream()
                .filter(qDecl -> qDecl.getQueue().equals(qName))
                .findAny();
        if (queueDeclaration.isEmpty()) {
            throw new IllegalArgumentException("No declaration for queue '" + qName + "' known");
        }

        final Topology.Builder builder = new Topology.Builder().addQueueDeclaration(queueDeclaration.get());

        final Map<String, List<AMQP.Queue.Bind>> bindings = topology.queueBindings()
                .stream()
                .filter(binding -> binding.getQueue().equals(qName))
                .collect(Collectors.groupingBy(AMQP.Queue.Bind::getExchange));
        bindings.values().forEach(builder::addQueueBindings);

        topology.exchangeDeclarations()
                .stream()
                .filter(exDecl -> bindings.containsKey(exDecl.getExchange()))
                .forEach(builder::addExchangeDeclaration);

        infrastructure.setUpTopology(builder.build(), channel);
    }

    private DispatchingConsumer createConsumer(String queueName) throws IOException {
        final FromQueue.Literal qualifier = new FromQueue.Literal(queueName);
        return new DispatchingConsumer(channelInstance.get(), dispatcher.select(qualifier));
    }

    @PreDestroy
    void cancelAllConsumers() throws IOException {
        try {
            lock.lock();
            if (destroyed) {
                return;
            }

            for (DispatchingConsumer consumer : consumers.values()) {
                final Channel channel = consumer.getChannel();
                channel.basicCancel(consumer.getConsumerTag());
                channelInstance.destroy(channel);
            }
            this.consumers.clear();
            this.destroyed = true;
        } finally {
            lock.unlock();
        }
    }

    private class DispatchingConsumer extends DefaultConsumer {

        private final Event<Delivery> qualifiedDispatcher;

        public DispatchingConsumer(Channel channel, Event<Delivery> qualifiedDispatcher) {
            super(channel);
            this.qualifiedDispatcher = qualifiedDispatcher;
        }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties,
                                   byte[] body) {
            final StringBuilder msgBuilder = new StringBuilder("Incoming RabbitMQ delivery from exchange=").append('\'')
                    .append(envelope.getExchange())
                    .append('\'')
                    .append(" with routing key='")
                    .append(envelope.getRoutingKey())
                    .append('\'');
            if (EVENT_BRIDGE_LOG.isLoggable(Level.FINE)) {
                msgBuilder.append(" and properties = ").append(properties);
            } else {
                msgBuilder.append(" and correlationId='").append(properties.getCorrelationId()).append('\'');
            }

            EVENT_BRIDGE_LOG.fine(msgBuilder::toString);

            final CompletionStage<Delivery> stage = qualifiedDispatcher.fireAsync(
                    new Delivery(envelope, properties, body)).whenComplete((result, ex) -> {
                if (ex != null) {
                    msgBuilder.append(" could not be handled.");
                    EVENT_BRIDGE_LOG.log(Level.SEVERE, msgBuilder.toString(), ex);
                }
            });
            runningEvents.add(stage);
            stage.whenComplete((result, failure) -> runningEvents.remove(stage));
        }

        /**
         * @see Consumer#handleCancel(String)
         */
        @Override
        public void handleCancel(String consumerTag) throws IOException {
            channelInstance.destroy(getChannel());
        }
    }
}