package io.github.jhahn.enhancedcdi.messaging.impl;

import com.rabbitmq.client.*;
import io.github.jhahn.enhancedcdi.messaging.Consumers;
import io.github.jhahn.enhancedcdi.messaging.MessageAcknowledgment;
import io.github.jhahn.enhancedcdi.messaging.messages.Incoming;

import javax.enterprise.event.Event;
import java.io.Closeable;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.lang.ref.Cleaner;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.github.jhahnhro.enhancedcdi.util.Cleaning.DEFAULT_CLEANER;

class DispatchingConsumer extends DefaultConsumer {

    private static final System.Logger LOG = System.getLogger(DispatchingConsumer.class.getName());

    private final String queueName;
    private final Consumers.ConsumerOptions options;
    private final Event<InternalDelivery> dispatcher;
    private final AtomicBoolean started;
    private final Phaser outstandingAcknowledgements;

    public DispatchingConsumer(final Channel channel, String queueName, Consumers.ConsumerOptions options,
                               Event<InternalDelivery> dispatcher) {
        super(channel);
        this.queueName = queueName;
        this.options = options;
        this.dispatcher = dispatcher;
        this.started = new AtomicBoolean(false);

        this.outstandingAcknowledgements = new Phaser();
        this.outstandingAcknowledgements.register();
    }

    @Override
    public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) {
        final String msg = ("Incoming RabbitMQ message from exchange=\"%s\" with routing key=\"%s\" and "
                            + "correlationId=\"%s\"").formatted(envelope.getExchange(), envelope.getRoutingKey(),
                                                                properties.getCorrelationId());

        LOG.log(Level.INFO, msg);

        Incoming<byte[]> incomingMessage = createIncomingMessage(envelope, properties, body, queueName);
        MessageAcknowledgment ack = createMessageAcknowledgement(envelope.getDeliveryTag(), msg);

        dispatcher.fireAsync(new InternalDelivery(incomingMessage, ack)).whenComplete((result, ex) -> {
            if (ex != null) {
                LOG.log(Level.ERROR, msg + " could not be handled.", ex);
            }
        });
    }

    private MessageAcknowledgment createMessageAcknowledgement(final long deliveryTag, String msg) {
        if (options.acknowledgementMode() == MessageAcknowledgment.Mode.AUTO) {
            return new MessageAcknowledgment.AutoAck();
        } else {
            return new ManualAck(deliveryTag, getChannel(), this.outstandingAcknowledgements, msg);
        }
    }

    private Incoming<byte[]> createIncomingMessage(Envelope envelope, AMQP.BasicProperties properties, byte[] body,
                                                   String queueName) {
        final Delivery delivery = new Delivery(envelope, properties, body);
        if (properties.getReplyTo() != null) {
            return new Incoming.Request<>(delivery, queueName, body);
        } else {
            return new Incoming.Cast<>(delivery, queueName, body);
        }
    }

    @Override
    public void handleCancelOk(String consumerTag) {
        // call to channel.cancel() was successful
    }

    @Override
    public void handleCancel(String consumerTag) throws IOException {
        // consumer was cancelled unexpectedly, for example because the queue was deleted on the broker
        this.started.set(false);
        this.closeChannel();
    }

    @Override
    public void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) {
        this.started.set(false);
        this.outstandingAcknowledgements.forceTermination();
    }

    public void stop() throws IOException {
        this.cancel();
        this.closeChannel();
    }

    private void cancel() throws IOException {
        if (this.started.compareAndSet(true, false)) {
            Channel channel = getChannel();
            channel.basicCancel(getConsumerTag());
        }
    }

    private void closeChannel() throws IOException {
        Channel channel = getChannel();
        // given all outstanding manual acknowledgements a chance to arrive
        final int phase = this.outstandingAcknowledgements.arriveAndDeregister();
        this.outstandingAcknowledgements.awaitAdvance(phase);

        if (phase >= 0) { // if the phaser is already terminated
            try {
                channel.close();
            } catch (TimeoutException e) {
                throw new IOException(e);
            }
        }
    }

    public void start() throws IOException {
        if (this.started.compareAndSet(false, true)) {
            getChannel().basicQos(options.qos());

            final boolean autoAck = options.acknowledgementMode() == MessageAcknowledgment.Mode.AUTO;
            getChannel().basicConsume(this.queueName, autoAck, this);
        }
    }

    private static class ManualAck implements MessageAcknowledgment, Closeable {
        private final long deliveryTag;
        private final Channel channel;
        private final AtomicBoolean calledExplicitly;
        private final Cleaner.Cleanable cleanable;

        private ManualAck(long deliveryTag, Channel channel, final Phaser phaser, final String msg) {
            this.deliveryTag = deliveryTag;
            this.channel = channel;
            this.calledExplicitly = new AtomicBoolean(false);

            phaser.register();
            this.cleanable = DEFAULT_CLEANER.register(this, new CleaningAction(phaser, this.calledExplicitly, msg));
        }

        @Override
        public void ack() throws IOException {
            if (this.calledExplicitly.compareAndSet(false, true)) {
                channel.basicAck(deliveryTag, false);
                close();
            }
        }

        @Override
        public void reject(final boolean requeue) throws IOException {
            if (this.calledExplicitly.compareAndSet(false, true)) {
                channel.basicReject(deliveryTag, requeue);
                close();
            }
        }

        /**
         * Called upon destruction of the Acknowledgement bean. Delegates to the cleaning action.
         */
        @Override
        public void close() {
            cleanable.clean();
        }

        private record CleaningAction(Phaser phaser, AtomicBoolean calledExplicitly, String msg) implements Runnable {
            @Override
            public void run() {
                if (!calledExplicitly.get()) {
                    LOG.log(Level.WARNING,
                            msg + " was neither acknowledged nor rejected even though manual acknowledgement was "
                            + "configured. The broker will re-enqueue the message once the consumer channel or "
                            + "connection is closed.");
                }
                phaser.arriveAndDeregister();
            }
        }
    }
}
