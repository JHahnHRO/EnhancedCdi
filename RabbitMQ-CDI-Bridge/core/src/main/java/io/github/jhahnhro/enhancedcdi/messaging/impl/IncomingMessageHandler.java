package io.github.jhahnhro.enhancedcdi.messaging.impl;

import java.io.IOException;
import java.lang.annotation.Annotation;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.event.ObservesAsync;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import com.rabbitmq.client.Envelope;
import io.github.jhahnhro.enhancedcdi.messaging.FromExchange;
import io.github.jhahnhro.enhancedcdi.messaging.FromQueue;
import io.github.jhahnhro.enhancedcdi.messaging.Redelivered;
import io.github.jhahnhro.enhancedcdi.messaging.WithRoutingKey;
import io.github.jhahnhro.enhancedcdi.messaging.impl.producers.MessageMetaDataProducer;
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
    Instance<MessageMetaDataProducer> messageMetaDataProducer;

    @Inject
    Serialization serialization;

    void handleDelivery(@ObservesAsync InternalDelivery incomingDelivery) {
        final MessageMetaDataProducer metaData = messageMetaDataProducer.get();
        try {
            // make sure request metadata is available in the current request scope for injection into event observers
            metaData.setRawMessage(incomingDelivery.rawMessage(), incomingDelivery.ack());
            
            Incoming<?> message = serialization.deserialize(incomingDelivery.rawMessage());

            metaData.setDeserializedMessage(message);

            fireEvent(message);
            incomingDelivery.ack().ack();
        } catch (Exception e) {
            LOG.log(System.Logger.Level.ERROR,
                    "Cannot handle incoming delivery. If it was not already acknowledged or rejected,"
                    + " it will be rejected now without re-queueing it.", e);
            try {
                incomingDelivery.ack().reject(false);
            } catch (IOException ex) {
                LOG.log(System.Logger.Level.ERROR, "Incoming delivery could not be rejected.", ex);
            }
        } finally {
            messageMetaDataProducer.destroy(metaData);
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
