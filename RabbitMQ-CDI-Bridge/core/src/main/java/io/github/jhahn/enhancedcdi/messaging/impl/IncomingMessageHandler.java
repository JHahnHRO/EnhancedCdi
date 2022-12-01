package io.github.jhahn.enhancedcdi.messaging.impl;

import com.rabbitmq.client.Envelope;
import io.github.jhahn.enhancedcdi.messaging.FromExchange;
import io.github.jhahn.enhancedcdi.messaging.FromQueue;
import io.github.jhahn.enhancedcdi.messaging.Redelivered;
import io.github.jhahn.enhancedcdi.messaging.WithRoutingKey;
import io.github.jhahn.enhancedcdi.messaging.impl.producers.MessageAcknowledgementProducer;
import io.github.jhahn.enhancedcdi.messaging.impl.producers.MessageMetaDataProducer;
import io.github.jhahn.enhancedcdi.messaging.impl.producers.ResponseBuilderProducer;
import io.github.jhahn.enhancedcdi.messaging.messages.Incoming;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.event.ObservesAsync;
import javax.inject.Inject;
import java.io.IOException;
import java.lang.annotation.Annotation;

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
    @io.github.jhahn.enhancedcdi.messaging.Incoming
    Event<Object> processedEvent;

    @Inject
    MessageMetaDataProducer messageMetaDataProducer;
    @Inject
    MessageAcknowledgementProducer messageAcknowledgementProducer;
    @Inject
    ResponseBuilderProducer responseBuilderProducer;

    @Inject
    Serialization serialization;

    void handleDelivery(@ObservesAsync InternalDelivery incomingDelivery) {
        try {
            prepareMetaData(incomingDelivery);
            Incoming<?> message = serialization.deserialize(incomingDelivery.rawMessage());

            messageMetaDataProducer.setDeserializedMessage(message);
            responseBuilderProducer.createResponseBuilderFor(message);

            fireEvent(message);
            incomingDelivery.ack().ack();
        } catch (Exception e) {
            LOG.log(System.Logger.Level.ERROR,
                    "Cannot handle incoming delivery. If it was not already acknowledged or rejected,"
                    + " it will be rejected now without re-queueing it.", e);
            try {
                incomingDelivery.ack().reject(false);
            } catch (IOException ex) {
                LOG.log(System.Logger.Level.ERROR, "Incoming delivery could not be rejected.", e);
            }
        }
    }

    private void prepareMetaData(InternalDelivery incomingDelivery) {
        // make sure request metadata is available in the current request scope for injection into event observers
        messageMetaDataProducer.setRawMessage(incomingDelivery.rawMessage());
        // make sure manual acknowledgement is possible in the current request scope
        messageAcknowledgementProducer.setAcknowledgement(incomingDelivery.ack());
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
