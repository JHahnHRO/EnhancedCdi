package io.github.jhahn.enhancedcdi.messaging.impl;

import com.rabbitmq.client.*;
import io.github.jhahn.enhancedcdi.messaging.*;
import io.github.jhahn.enhancedcdi.messaging.processing.Outgoing;
import io.github.jhahn.enhancedcdi.messaging.processing.ProcessIncoming;
import io.github.jhahn.enhancedcdi.messaging.serialization.Deserialized;
import io.github.jhahn.enhancedcdi.messaging.serialization.Deserializer;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.control.RequestContextController;
import javax.enterprise.event.Event;
import javax.enterprise.event.ObserverException;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 */
@ApplicationScoped
class EventBridge {

    private final static Logger LOG = Logger.getLogger(EventBridge.class.getCanonicalName());

    @Inject
    BrokerConnector brokerConnector;
    @Inject
    RequestContextController requestContextController;
    /**
     * Fired for all incoming, unprompted deliveries, i.e. everything except RPC responses
     */
    @Inject
    Event<ProcessIncomingImpl> processIncomingEvent;
    /**
     * Fired for all outgoing deliveries
     */
    @Inject
    Event<ProcessOutgoingImpl<?>> processOutgoingEvent;
    /**
     * Fired if preprocessing has finished and payload was deserialized
     */
    @Inject
    Event<Object> processedEvent;

    @Inject
    MessageMetaData messageMetaData;

    @Inject
    Serialization serialization;

    //region incoming deliveries

    DeliverCallback createDeliveryCallback(String queueName) {
        return (String consumerTag, Delivery delivery) -> this.handleUnpromptedDelivery(delivery, queueName);
    }

    private void handleUnpromptedDelivery(Delivery delivery, String queueName) {
        try {
            requestContextController.activate();
            // make sure request metadata is available in the current request scope for injection into event observers
            messageMetaData.setIncomingMessage(delivery);
            messageMetaData.setIncomingQueue(queueName);

            ProcessIncomingImpl incomingDelivery = doPreprocessing(delivery, queueName);

            // processing the delivery
            if (incomingDelivery instanceof ProcessIncomingRequest incomingRequest
                && incomingRequest.immediateResponse().isPresent()) {
                sendImmediateResponse(delivery, incomingRequest.immediateResponse().get());
            } else if (incomingDelivery.vetoed) {
                LOG.info("Incoming RabbitMQ delivery was vetoed and will not be handled.");
            } else {
                fireEventForIncomingMessage(delivery, queueName, incomingDelivery);
            }
        } catch (IOException | TimeoutException | RuntimeException e) {
            LOG.log(Level.SEVERE, "Incoming RabbitMQ delivery could not be handled.", e);
        } finally {
            requestContextController.deactivate();
        }
    }

    private ProcessIncomingImpl doPreprocessing(Delivery message, String queueName) {
        ProcessIncomingImpl incomingDelivery = createProcessIncomingDelivery(message, queueName);
        try {
            processIncomingEvent.fire(incomingDelivery);
        } catch (RuntimeException e) {
            final Throwable cause = (e instanceof ObserverException) ? e.getCause() : e;

            throw new RuntimeException(
                    "Preprocessing of an incoming delivery (Queue = '" + queueName + "', properties = "
                    + message.getProperties() + ") failed. It has been dropped, no response has been sent.", cause);
        }
        return incomingDelivery;
    }

    private void sendImmediateResponse(Delivery request, ProcessIncoming.Request.ImmediateResponse immediateResponse)
            throws IOException, TimeoutException {

        final AMQP.BasicProperties properties = new PropertiesBuilderImpl().of(immediateResponse.properties()).build();
        brokerConnector.fireAndForget("", request.getProperties().getReplyTo(), properties, immediateResponse.body());
    }

    private void fireEventForIncomingMessage(Delivery message, String queueName, ProcessIncomingImpl incomingDelivery)
            throws IOException {

        Deserializer<?> deserializer = incomingDelivery.deserializer;
        if (deserializer == null) {
            deserializer = serialization.selectDeserializer(incomingDelivery.properties()).orElseThrow();
        }
        // deserialize request payload into Java object
        Deserialized<?> payload = deserializer.deserialize(incomingDelivery.stream, incomingDelivery.properties());

        processedEvent.select(getStandardQualifiers(message.getEnvelope(), queueName)).fire(payload.message());
    }

    private Annotation[] getStandardQualifiers(Envelope messageEnvelope, String queueName) {
        return new Annotation[]{Incoming.Literal.INSTANCE, new FromQueue.Literal(queueName), new FromExchange.Literal(
                messageEnvelope.getExchange()), new WithRoutingKey.Literal(
                messageEnvelope.getRoutingKey()), Redelivery.Literal.of(messageEnvelope.isRedeliver())};
    }

    private ProcessIncomingImpl createProcessIncomingDelivery(Delivery message, String queue) {
        if (message.getProperties().getReplyTo() != null) {
            return new ProcessIncomingRequest(message, queue);
        } else {
            return new ProcessIncomingMessage(message, queue);
        }
        // Incoming response are handled elsewhere
    }

    //endregion

    //region Outgoing deliveries
    void afterProcessingOutgoing(@Observes Outgoing<?> outgoing) throws IOException, TimeoutException {
        final ProcessOutgoingImpl<?> processingEvent = createProcessingEvent(outgoing);
        processOutgoingEvent.fire(processingEvent);

        if (processingEvent instanceof ProcessOutgoingMessage || processingEvent instanceof ProcessOutgoingResponse) {
            serializeFireAndForget(processingEvent);
        } else {
            serializeAndRpc((ProcessOutgoingRequest<?>) processingEvent);
        }
    }

    private void serializeAndRpc(ProcessOutgoingRequest<?> processedEvent) throws IOException, TimeoutException {
        byte[] bytes = null;
        final RpcClient.Response response = brokerConnector.doRpc(processedEvent.exchange(),
                                                                  processedEvent.routingKey(),
                                                                  processedEvent.properties().build(), bytes);


        this.handleUnpromptedDelivery(
                new Delivery(response.getEnvelope(), response.getProperties(), response.getBody()), null);
    }

    private <T> ProcessOutgoingImpl<T> createProcessingEvent(Outgoing<T> outgoing) {
        if (outgoing.properties().getReplyTo() != null) {
            return new ProcessOutgoingRequest<>(outgoing);
        }
        if (messageMetaData.basicProperties().getReplyTo() != null) {
            return new ProcessOutgoingResponse<>(outgoing, messageMetaData.incomingMessage);
        }
        return new ProcessOutgoingMessage<>(outgoing);
    }

    private void serializeFireAndForget(ProcessOutgoingImpl<?> processOutgoing) throws IOException, TimeoutException {
        // TODO Serialize
        final byte[] bytes = null;


        brokerConnector.fireAndForget(processOutgoing.exchange(), processOutgoing.routingKey(),
                                      processOutgoing.properties().build(), bytes);
    }

    //endregion
}
