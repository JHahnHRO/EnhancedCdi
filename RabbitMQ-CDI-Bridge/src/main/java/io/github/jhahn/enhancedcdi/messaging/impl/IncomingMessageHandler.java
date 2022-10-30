package io.github.jhahn.enhancedcdi.messaging.impl;

import com.rabbitmq.client.Envelope;
import io.github.jhahn.enhancedcdi.messaging.FromExchange;
import io.github.jhahn.enhancedcdi.messaging.FromQueue;
import io.github.jhahn.enhancedcdi.messaging.Redelivered;
import io.github.jhahn.enhancedcdi.messaging.impl.producers.MessageAcknowledgementProducer;
import io.github.jhahn.enhancedcdi.messaging.impl.producers.MessageMetaDataProducer;
import io.github.jhahn.enhancedcdi.messaging.impl.producers.ResponseBuilderProducer;
import io.github.jhahn.enhancedcdi.messaging.messages.Incoming;
import io.github.jhahn.enhancedcdi.messaging.rpc.WithRoutingKey;

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

    void handleDelivery(@ObservesAsync InternalDelivery incomingDelivery) throws IOException {
        prepareMetaData(incomingDelivery);

        final Incoming<?> message = serialization.deserialize(incomingDelivery.rawMessage());

        prepareResponseBuilder(message);
        fireEvent(message);
    }

    private void prepareMetaData(InternalDelivery incomingDelivery) {
        // make sure request metadata is available in the current request scope for injection into event observers
        messageMetaDataProducer.setMetaData(incomingDelivery.rawMessage());
        // make sure manual acknowledgement is possible in the current request scope
        messageAcknowledgementProducer.setAcknowledgement(incomingDelivery.ack());
    }

    private void prepareResponseBuilder(Incoming<?> message) {
        if (message instanceof Incoming.Request<?> request) {
            responseBuilderProducer.createResponseBuilderFor(request);
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
