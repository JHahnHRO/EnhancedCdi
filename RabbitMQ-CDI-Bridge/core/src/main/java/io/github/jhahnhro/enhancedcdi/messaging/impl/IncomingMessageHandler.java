package io.github.jhahnhro.enhancedcdi.messaging.impl;

import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.WARNING;

import java.io.IOException;
import java.lang.annotation.Annotation;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.event.ObservesAsync;
import javax.inject.Inject;

import com.rabbitmq.client.Envelope;
import io.github.jhahnhro.enhancedcdi.messaging.FromExchange;
import io.github.jhahnhro.enhancedcdi.messaging.FromQueue;
import io.github.jhahnhro.enhancedcdi.messaging.Redelivered;
import io.github.jhahnhro.enhancedcdi.messaging.WithRoutingKey;
import io.github.jhahnhro.enhancedcdi.messaging.impl.producers.MessageMetaDataProducer;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Acknowledgment;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;

/**
 *
 */
@ApplicationScoped
class IncomingMessageHandler {

    private static final System.Logger LOG = System.getLogger(IncomingMessageHandler.class.getName());
    /**
     * Fired if preprocessing has finished and payload was deserialized
     */
    @Inject
    @io.github.jhahnhro.enhancedcdi.messaging.Incoming
    Event<Object> processedEvent;

    @Inject
    MessageMetaDataProducer metaData;

    @Inject
    Serialization serialization;

    void handleDelivery(@ObservesAsync InternalDelivery incomingDelivery) {
        final Incoming<byte[]> rawMessage = incomingDelivery.rawMessage();
        final Acknowledgment acknowledgment = incomingDelivery.ack();

        try {
            // make sure request metadata is available in the current request scope for injection into event observers
            metaData.setRawMessage(rawMessage, acknowledgment);

            Incoming<?> message = serialization.deserialize(rawMessage);

            metaData.setDeserializedMessage(message);

            fireEvent(message);

            acknowledgeIfNecessary(acknowledgment);
        } catch (RpcException e) {
            throw e;
        } catch (Exception e) {
            rejectIfNecessary(acknowledgment, e);
        }
    }

    private void acknowledgeIfNecessary(Acknowledgment acknowledgment) throws IOException {
        if (acknowledgment.getState() == Acknowledgment.State.UNACKNOWLEDGED) {
            LOG.log(WARNING, "Incoming delivery in manual acknowledge mode was not explicitly acknowledged. "
                             + "That is probably an error. It will be acknowledged now.");
            acknowledgment.ack();
        }
    }

    private void rejectIfNecessary(Acknowledgment acknowledgment, Exception e) {
        if (acknowledgment.getState() == Acknowledgment.State.UNACKNOWLEDGED) {
            LOG.log(ERROR, "Could not handle incoming delivery. It will be rejected now without re-queueing it.", e);
            try {
                acknowledgment.reject(false);
            } catch (IOException ex) {
                LOG.log(ERROR, "Incoming delivery could not be rejected.", ex);
            }
        } else {
            LOG.log(ERROR, "Could not handle incoming delivery.", e);
        }
    }

    private void fireEvent(Incoming<?> message) {
        processedEvent.select(getStandardQualifiers(message.delivery().getEnvelope(), message.queue()))
                .fire(message.content());
    }

    private Annotation[] getStandardQualifiers(Envelope messageEnvelope, String queueName) {
        return new Annotation[]{new FromQueue.Literal(queueName), new FromExchange.Literal(
                messageEnvelope.getExchange()), new WithRoutingKey.Literal(
                messageEnvelope.getRoutingKey()), Redelivered.Literal.of(messageEnvelope.isRedeliver())};
    }
}
