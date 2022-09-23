package io.github.jhahn.enhancedcdi.messaging.impl;

import com.rabbitmq.client.*;
import io.github.jhahn.enhancedcdi.messaging.*;

import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

@ApplicationScoped
class ConsumerRegistry {
    private static final System.Logger LOG = System.getLogger(EventBridge.class.getCanonicalName());

    /**
     * Map queue names => Consumers (Because we only dispatch message to the CDI event system, there only ever needs to
     * be one consumer per queue)
     */
    private final Map<String, DispatchingConsumer> consumers = new ConcurrentHashMap<>();
    @Inject
    @Incoming
    Event<Delivery> dispatcher;
    @Inject
    Connection connection;
    @Inject
    Infrastructure infrastructure;

    public void startReceiving(@Observes StartReceiving startReceiving) throws IOException {
        final String queueName = startReceiving.queue();
        final Channel channel = createChannel(queueName);
        infrastructure.setUpForQueue(queueName);
        createAndStartConsumer(queueName, channel);
    }

    public void stopReceiving(@Observes StopReceiving stopReceiving) {
        try {
            final DispatchingConsumer consumer = consumers.remove(stopReceiving.queue());
            if (consumer == null) {
                return;
            }
            stopConsumer(consumer);
        } catch (IOException | TimeoutException e) {
            LOG.log(Level.WARNING,
                    "Consumer for queue " + stopReceiving.queue() + " was stopped, but that threw an exception.", e);
        }
    }

    @PreDestroy
    void stopAllConsumers() throws IOException, TimeoutException {
        for (DispatchingConsumer consumer : consumers.values()) {
            stopConsumer(consumer);
        }
        this.consumers.clear();
    }

    private Channel createChannel(String queueName) throws IOException {
        final Channel channel = connection.createChannel();
        if (channel == null) {
            throw new IllegalStateException("No channel available");
        }
        channel.addShutdownListener(shutdownSignalException -> {
            LOG.log(Level.INFO, "Channel for consumer on queue '%s' was shutdown. Reason was '%s'.", queueName,
                    shutdownSignalException.getReason());
            consumers.remove(queueName);
        });
        return channel;
    }

    private void createAndStartConsumer(String queueName, Channel channel) throws IOException {
        final DispatchingConsumer consumer = new DispatchingConsumer(channel, queueName, dispatcher.select(
                new FromQueue.Literal(queueName)));
        consumers.put(queueName, consumer);
        channel.basicConsume(queueName, true, consumer);
    }


    private void stopConsumer(DispatchingConsumer consumer) throws IOException, TimeoutException {
        final Channel channel = consumer.getChannel();
        channel.basicCancel(consumer.getConsumerTag());
        channel.close();
    }

    private class DispatchingConsumer extends DefaultConsumer {
        private final String queueName;
        private final Event<Delivery> qualifiedDispatcher;

        public DispatchingConsumer(final Channel channel, String queueName, final Event<Delivery> qualifiedDispatcher) {
            super(channel);
            this.queueName = queueName;
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
                    .append('\'')
                    .append(" and correlationId='")
                    .append(properties.getCorrelationId())
                    .append('\'');

            LOG.log(Level.INFO, msgBuilder::toString);

            qualifiedDispatcher.fireAsync(new Delivery(envelope, properties, body)).whenComplete((result, ex) -> {
                if (ex != null) {
                    msgBuilder.append(" could not be handled.");
                    LOG.log(Level.ERROR, msgBuilder::toString, ex);
                }
            });
        }

        @Override
        public void handleCancelOk(String consumerTag) {
            //
        }

        @Override
        public void handleCancel(String consumerTag) throws IOException {
            LOG.log(Level.WARNING, "Consumer on queue " + queueName + " was unexpectedly cancelled");
            consumers.remove(queueName);
        }
    }
}