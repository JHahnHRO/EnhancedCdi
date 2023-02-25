package io.github.jhahnhro.enhancedcdi.messaging.impl;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.enterprise.event.Event;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;
import io.github.jhahnhro.enhancedcdi.messaging.Consumers;
import io.github.jhahnhro.enhancedcdi.messaging.MessageAcknowledgment;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;

class DispatchingConsumer extends DefaultConsumer {

    private static final System.Logger LOG = System.getLogger(DispatchingConsumer.class.getName());

    private final String queueName;
    private final Consumers.Options options;
    private final Event<InternalDelivery> dispatcher;

    public DispatchingConsumer(final Channel channel, String queueName, Consumers.Options options,
                               Event<InternalDelivery> dispatcher) {
        super(channel);
        this.queueName = queueName;
        this.options = options;
        this.dispatcher = dispatcher;
    }

    @Override
    public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) {
        final String msg = ("Incoming RabbitMQ message from exchange=\"%s\" with routing key=\"%s\" and "
                            + "correlationId=\"%s\"").formatted(envelope.getExchange(), envelope.getRoutingKey(),
                                                                properties.getCorrelationId());

        LOG.log(Level.INFO, msg);

        Incoming<byte[]> incomingMessage = createIncomingMessage(envelope, properties, body, queueName);
        MessageAcknowledgment ack = createMessageAcknowledgement(envelope.getDeliveryTag());

        dispatcher.fireAsync(new InternalDelivery(incomingMessage, ack)).whenComplete((result, ex) -> {
            if (ex != null) {
                LOG.log(Level.ERROR, msg + " could not be handled.", ex);
            }
        });
    }

    private MessageAcknowledgment createMessageAcknowledgement(final long deliveryTag) {
        return options.autoAck() ? AutoAck.INSTANCE : new ManualAck(deliveryTag, getChannel());
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
        LOG.log(Level.WARNING, "Consumer on queue \"%s\" (consumerTag \"%s\") was cancelled unexpectedly.", queueName,
                consumerTag);
        this.closeChannel();
    }

    @Override
    public void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) {
        LOG.log(Level.INFO,
                "Channel for consumer on queue \"%s\" (consumerTag \"%s\") was shut down. Reason was \"%s\".",
                queueName, consumerTag, sig.getReason());
    }

    public void stop() throws IOException {
        this.cancel();
        this.closeChannel();
    }

    private void cancel() throws IOException {
        Channel channel = getChannel();
        try {
            channel.basicCancel(getConsumerTag());
        } catch (ShutdownSignalException sig) {
            // nothing to do if channel is already closed
        }
    }

    private void closeChannel() throws IOException {
        Channel channel = getChannel();
        try {
            channel.close();
        } catch (ShutdownSignalException sse) {
            // already closed
        } catch (TimeoutException e) {
            throw new IOException(e);
        }
    }

    public void start() throws IOException {
        final Channel channel = getChannel();
        try {
            channel.basicQos(options.qos());
            channel.basicConsume(this.queueName, options.autoAck(), this);
        } catch (ShutdownSignalException sig) {
            LOG.log(Level.ERROR,
                    "Cannot start consumer on queue \"%s\", because channel is already closed (reason was \"%s\").",
                    queueName, sig.getReason());
        }
    }

    private static class ManualAck implements MessageAcknowledgment {
        private final long deliveryTag;
        private final Channel channel;
        private final AtomicBoolean alreadyDone;

        private ManualAck(long deliveryTag, Channel channel) {
            this.deliveryTag = deliveryTag;
            this.channel = channel;
            this.alreadyDone = new AtomicBoolean(false);
        }

        @Override
        public void ack() throws IOException {
            if (this.alreadyDone.compareAndSet(false, true)) {
                try {
                    channel.basicAck(deliveryTag, false);
                } catch (ShutdownSignalException sse) {
                    LOG.log(Level.WARNING, "Cannot acknowledge message, "
                                           + "because the channel on which it was received is already closed. "
                                           + "The broker will re-queue the message anyway.");
                }
            }
        }

        @Override
        public void reject(final boolean requeue) throws IOException {
            if (this.alreadyDone.compareAndSet(false, true)) {
                try {
                    channel.basicReject(deliveryTag, requeue);
                } catch (ShutdownSignalException sse) {
                    LOG.log(Level.WARNING, "Cannot reject message, "
                                           + "because the channel on which it was received is already closed. "
                                           + "The broker will re-queue the message anyway.");
                }
            }
        }
    }
}
