package io.github.jhahnhro.enhancedcdi.messaging.impl;

import static io.github.jhahnhro.enhancedcdi.messaging.messages.Acknowledgement.State.*;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.util.Map;
import java.util.concurrent.Semaphore;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;
import io.github.jhahnhro.enhancedcdi.messaging.Consumers;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Acknowledgement;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;
import jakarta.enterprise.event.Event;

class DispatchingConsumer extends DefaultConsumer {

    private static final String SERVER_GENERATED_CONSUMER_TAG = "";
    private static final System.Logger LOG = System.getLogger(DispatchingConsumer.class.getName());

    private final String queueName;
    private final Consumers.Options options;
    private final Event<InternalDelivery> dispatcher;
    // guards against concurrent writes to the channel during acknowledgment
    private final Semaphore ackPermit;

    public DispatchingConsumer(final Channel channel, String queueName, Consumers.Options options,
                               Event<InternalDelivery> dispatcher) {
        super(channel);
        this.queueName = queueName;
        this.options = options;
        this.dispatcher = dispatcher;
        this.ackPermit = options.autoAck() ? null : new Semaphore(1);
    }

    @Override
    public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) {
        final String msg = ("Incoming RabbitMQ message from exchange=\"%s\" with routing key=\"%s\" and "
                            + "correlationId=\"%s\"").formatted(envelope.getExchange(), envelope.getRoutingKey(),
                                                                properties.getCorrelationId());

        LOG.log(Level.INFO, msg);

        Incoming<byte[]> incomingMessage = createIncomingMessage(envelope, properties, body, queueName);
        Acknowledgement ack = createMessageAcknowledgement(envelope.getDeliveryTag());

        dispatcher.fireAsync(new InternalDelivery(incomingMessage, ack)).whenComplete((result, ex) -> {
            if (ex != null) {
                LOG.log(Level.ERROR, msg + " could not be handled.", ex);
            }
        });
    }

    private Acknowledgement createMessageAcknowledgement(final long deliveryTag) {
        return options.autoAck() ? AutoAck.INSTANCE : new ManualAck(deliveryTag, getChannel(), ackPermit);
    }

    private Incoming<byte[]> createIncomingMessage(Envelope envelope, AMQP.BasicProperties properties, byte[] body,
                                                   String queueName) {
        if (properties.getReplyTo() != null) {
            return new Incoming.Request<>(queueName, envelope, properties, body);
        } else {
            return new Incoming.Cast<>(queueName, envelope, properties, body);
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
        this.cancelConsumer();
        this.closeChannel();
    }

    private void cancelConsumer() throws IOException {
        Channel channel = getChannel();
        try {
            channel.basicCancel(getConsumerTag());
        } catch (AlreadyClosedException sig) {
            // nothing to do if channel is already closed
        }
    }

    private void closeChannel() {
        Channel channel = getChannel();
        try {
            channel.abort();
        } catch (IOException ignored) {
            // abort does not throw this exception. It's just specified in the interface for backwards compatibility.
        }
    }

    public void start() throws IOException {
        final Channel channel = getChannel();
        channel.basicQos(options.qos());

        // RabbitMQ Client lib uses null instead of empty map for absence of arguments
        final Map<String, Object> arguments = options.arguments().isEmpty() ? null : options.arguments();

        channel.basicConsume(this.queueName, options.autoAck(), SERVER_GENERATED_CONSUMER_TAG, false,
                             options.exclusive(), arguments, this);
    }

    private static class ManualAck implements Acknowledgement {
        private final long deliveryTag;
        private final Channel channel;
        private final Semaphore ackPermit;
        private State state;

        private ManualAck(long deliveryTag, Channel channel, Semaphore ackPermit) {
            this.deliveryTag = deliveryTag;
            this.channel = channel;
            this.ackPermit = ackPermit;
            this.state = UNACKNOWLEDGED;
        }

        @Override
        public void ack() throws IOException {
            if (this.state == UNACKNOWLEDGED) {
                try {
                    ackPermit.acquireUninterruptibly();
                    channel.basicAck(deliveryTag, false);
                    this.state = ACKNOWLEDGED;
                } catch (AlreadyClosedException ace) {
                    LOG.log(Level.WARNING, "Cannot acknowledge message, "
                                           + "because the channel on which it was received is already closed. "
                                           + "The broker will re-queue the message anyway.");
                } finally {
                    ackPermit.release();
                }
            } else if (this.state == REJECTED) {
                throw new IllegalStateException("Message cannot be acknowledged, because has already been rejected");
            }
        }

        @Override
        public void reject(final boolean requeue) throws IOException {
            if (this.state == UNACKNOWLEDGED) {
                try {
                    ackPermit.acquireUninterruptibly();
                    channel.basicReject(deliveryTag, requeue);
                    this.state = REJECTED;
                } catch (AlreadyClosedException ace) {
                    LOG.log(Level.WARNING, "Cannot reject message, "
                                           + "because the channel on which it was received is already closed. "
                                           + "The broker will re-queue the message anyway.");
                } finally {
                    ackPermit.release();
                }
            } else if (this.state == ACKNOWLEDGED) {
                throw new IllegalStateException("Message cannot be rejected, because has already been acknowledged");
            }
        }

        @Override
        public State getState() {
            return this.state;
        }
    }
}
