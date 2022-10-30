package io.github.jhahn.enhancedcdi.messaging.impl;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ShutdownSignalException;
import io.github.jhahn.enhancedcdi.messaging.Consumers;

import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.WARNING;

@ApplicationScoped
class ConsumerRegistry implements Consumers {
    private static final System.Logger LOG = System.getLogger(ConsumerRegistry.class.getCanonicalName());

    /**
     * Map queue names => Consumers (Because we only dispatch message to the CDI event system, there only ever needs to
     * be one consumer per queue)
     */
    private final Map<String, DispatchingConsumer> consumers = new ConcurrentHashMap<>();
    @Inject
    Event<InternalDelivery> dispatcher;
    @Inject
    Connection connection;
    @Inject
    Infrastructure infrastructure;

    @Override
    public void startReceiving(String queue, ConsumerOptions options) throws IOException, InterruptedException {
        infrastructure.setUpForQueue(queue);
        final Channel channel = createChannel();

        final DispatchingConsumer consumer = createConsumer(queue, channel, options);
        consumers.put(queue, consumer);

        consumer.start();
    }

    @Override
    public void stopReceiving(String queue) throws IOException {
        final DispatchingConsumer consumer = consumers.remove(queue);
        if (consumer == null) {
            return;
        }
        consumer.stop();
    }

    @PreDestroy
    void stopRemainingConsumers() {
        for (var entry : consumers.entrySet()) {
            final String queueName = entry.getKey();
            final DispatchingConsumer consumer = entry.getValue();
            try {
                consumer.stop();
            } catch (IOException ex) {
                LOG.log(ERROR, "Consumer for queue \"" + queueName
                               + "\" could not be shut down in an orderly fashion. Continuing anyway.", ex);
            }
        }
    }

    private Channel createChannel() throws IOException {
        final Channel channel = connection.createChannel();
        if (channel == null) {
            throw new IllegalStateException("No channel available");
        }
        return channel;
    }

    private DispatchingConsumer createConsumer(final String queueName, final Channel channel, ConsumerOptions options) {
        return new DispatchingConsumer(channel, queueName, options, dispatcher) {
            @Override
            public void handleCancel(String consumerTag) throws IOException {
                super.handleCancel(consumerTag);
                LOG.log(WARNING, "Consumer on queue \"%s\" (consumerTag \"%s\") was cancelled unexpectedly.", queueName,
                        consumerTag);
                consumers.remove(queueName);
            }

            @Override
            public void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) {
                super.handleShutdownSignal(consumerTag, sig);
                LOG.log(Level.INFO,
                        "Channel for consumer on queue \"%s\" (consumerTag \"%s\") was shut down. Reason was \"%s\".",
                        queueName, consumerTag, sig.getReason());
                consumers.remove(queueName);
            }
        };
    }

}