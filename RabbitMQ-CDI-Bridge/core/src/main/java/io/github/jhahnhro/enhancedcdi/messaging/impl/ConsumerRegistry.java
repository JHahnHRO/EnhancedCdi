package io.github.jhahnhro.enhancedcdi.messaging.impl;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ShutdownSignalException;
import io.github.jhahnhro.enhancedcdi.messaging.Consumers;

@ApplicationScoped
class ConsumerRegistry implements Consumers {
    private static final System.Logger LOG = System.getLogger(ConsumerRegistry.class.getCanonicalName());

    /**
     * Map queue names => Consumers (Because we only dispatch message to the CDI event system, there only ever needs to
     * be one consumer per queue)
     */
    private final Map<String, DispatchingConsumer> consumers = new ConcurrentHashMap<>();
    private final Map<String, Lock> locks = new ConcurrentHashMap<>();
    @Inject
    Event<InternalDelivery> dispatcher;
    @Inject
    Connection connection;
    @Inject
    Infrastructure infrastructure;

    @Override
    public void startReceiving(String queue, Options options) throws IOException {
        underQueueLock(queue, queueName -> startInternal(queueName, options));
    }

    private void underQueueLock(String queue, QueueAction action) throws IOException {
        final Lock lock = locks.computeIfAbsent(queue, __ -> new ReentrantLock());
        try {
            lock.lock();
            action.run(queue);
        } finally {
            lock.unlock();
        }
    }

    private void startInternal(String queue, Options options) throws IOException {
        final DispatchingConsumer previousConsumer = consumers.remove(queue);
        if (previousConsumer != null) {
            previousConsumer.stop();
        }

        final Channel channel = connection.openChannel()
                .orElseThrow(() -> new IllegalStateException("No channel available"));

        final DispatchingConsumer consumer = new RegistryAwareConsumer(channel, queue, options);
        consumers.put(queue, consumer);

        infrastructure.setUpForQueue(queue, channel);
        consumer.start();
    }

    @Override
    public void stopReceiving(String queue) throws IOException {
        underQueueLock(queue, this::stopInternal);
    }

    private void stopInternal(String queue) throws IOException {
        final DispatchingConsumer consumer = consumers.remove(queue);
        if (consumer != null) {
            consumer.stop();
        }
    }

    @PreDestroy
    void stopRemainingConsumers() {
        consumers.forEach((queueName, consumer) -> {
            try {
                consumer.stop();
            } catch (IOException ex) {
                final String msg = ("Consumer for queue \"%s\" could not be shut down in an orderly fashion. "
                                    + "Continuing anyway.").formatted(queueName);
                LOG.log(Level.ERROR, msg, ex);
            }
        });
    }

    private class RegistryAwareConsumer extends DispatchingConsumer {

        private final String queueName;

        public RegistryAwareConsumer(Channel channel, String queueName, Options options) {
            super(channel, queueName, options, dispatcher);
            this.queueName = queueName;
        }

        @Override
        public void handleCancel(String consumerTag) throws IOException {
            super.handleCancel(consumerTag);
            consumers.remove(queueName);
        }

        @Override
        public void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) {
            super.handleShutdownSignal(consumerTag, sig);
            consumers.remove(queueName);
        }

    }

    @FunctionalInterface
    private interface QueueAction {
        void run(String queue) throws IOException;
    }
}