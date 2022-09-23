package io.github.jhahn.enhancedcdi.messaging.impl;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.RpcClient;
import io.github.jhahn.enhancedcdi.messaging.*;
import io.github.jhahn.enhancedcdi.messaging.processing.Outgoing;
import io.github.jhahn.enhancedcdi.messaging.processing.ProcessIncoming;
import io.github.jhahn.enhancedcdi.messaging.serialization.Deserializer;
import io.github.jhahn.enhancedcdi.messaging.serialization.SerializationSelector;
import io.github.jhahn.enhancedcdi.messaging.serialization.Serializer;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.event.ObserverException;
import javax.enterprise.event.Observes;
import javax.enterprise.event.ObservesAsync;
import javax.enterprise.inject.spi.EventMetadata;
import javax.inject.Inject;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.Optional;

import static java.lang.System.Logger.Level.INFO;

/**
 *
 */
@ApplicationScoped
class EventBridge {

    private final static System.Logger LOG = System.getLogger(EventBridge.class.getCanonicalName());

    @Inject
    LowLevelPublisher publisher;
    /**
     * Fired for all incoming, unprompted deliveries, i.e. everything except RPC responses
     */
    @Inject
    Event<ProcessIncomingImpl> preprocessingEvent;
    /**
     * Fired for all outgoing deliveries
     */
    @Inject
    Event<ProcessOutgoingImpl<?>> processOutgoingEvent;
    /**
     * Fired if preprocessing has finished and payload was deserialized
     */
    @Inject
    @Incoming
    Event<Object> processedEvent;
    /**
     * Fired to dispatch a response to an RPC request
     */
    @Inject
    @Incoming
    Event<Delivery> responseDeliveryEvent;

    @Inject
    MessageMetaData messageMetaData;

    @Inject
    SerializationSelector serializationSelector;

    //region incoming deliveries
    void handleDelivery(@ObservesAsync @Incoming Delivery delivery, EventMetadata eventMetadata)
            throws IOException, InterruptedException {
        final String queueName = getQueueName(eventMetadata);

        // make sure request metadata is available in the current request scope for injection into event observers
        messageMetaData.setIncomingMessage(delivery);
        messageMetaData.setIncomingQueue(queueName);

        ProcessIncomingImpl preprocessingEvent = doPreprocessing(delivery, queueName);

        // processing the delivery
        if (preprocessingEvent.vetoed) {
            LOG.log(INFO, "Incoming RabbitMQ delivery was vetoed and will not be handled.");
        } else {
            deserializeAndFire(delivery, queueName, preprocessingEvent);
        }
    }

    private String getQueueName(EventMetadata eventMetadata) {
        return eventMetadata.getQualifiers()
                .stream()
                .filter(FromQueue.class::isInstance)
                .map(FromQueue.class::cast)
                .map(FromQueue::value)
                .findAny()
                .orElseThrow();
    }

    private ProcessIncomingImpl doPreprocessing(Delivery delivery, String queueName)
            throws IOException, InterruptedException {
        ProcessIncomingImpl preprocessingEvent = createProcessingEvent(delivery, queueName);
        try {
            this.preprocessingEvent.fire(preprocessingEvent);
            return preprocessingEvent;
        } catch (RuntimeException e) {
            final Throwable cause = (e instanceof ObserverException) ? e.getCause() : e;
            throw new RuntimeException("Preprocessing failed.", cause);
        } finally {
            if (preprocessingEvent instanceof ProcessIncomingRequest incomingRequest) {
                final Optional<ProcessIncoming.Request.ImmediateResponse> immediateResponse =
                        incomingRequest.immediateResponse();
                if (immediateResponse.isPresent()) {
                    sendImmediateResponse(delivery, immediateResponse.get());
                }
            }
        }
    }

    private void sendImmediateResponse(Delivery request, ProcessIncoming.Request.ImmediateResponse immediateResponse)
            throws IOException, InterruptedException {

        final AMQP.BasicProperties properties = new PropertiesBuilderImpl().of(immediateResponse.properties()).build();
        publisher.doBroadcast("", request.getProperties().getReplyTo(), properties, immediateResponse.body());
    }

    private void deserializeAndFire(Delivery delivery, String queueName, ProcessIncomingImpl preprocessedEvent)
            throws IOException {

        Deserializer<?> deserializer = preprocessedEvent.deserializer;
        if (deserializer == null) {
            deserializer = serializationSelector.selectDeserializer(delivery)
                    .orElseThrow(() -> new IllegalStateException("No Deserializer applicable"));
        }

        Object payload;
        try {
            payload = deserializer.deserialize(delivery.getEnvelope(), preprocessedEvent.properties(),
                                               preprocessedEvent.stream);
        } finally {
            preprocessedEvent.stream.close();
        }

        processedEvent.select(getStandardQualifiers(delivery.getEnvelope(), queueName)).fire(payload);
    }

    private Annotation[] getStandardQualifiers(Envelope messageEnvelope, String queueName) {
        return new Annotation[]{new FromQueue.Literal(queueName), new FromExchange.Literal(
                messageEnvelope.getExchange()), new WithRoutingKey.Literal(
                messageEnvelope.getRoutingKey()), Redelivered.Literal.of(messageEnvelope.isRedeliver())};
    }

    private ProcessIncomingImpl createProcessingEvent(Delivery delivery, String queue) {
        if (delivery instanceof ResponseDelivery<?> responseDelivery) {
            return new ProcessIncomingResponse<>(delivery, responseDelivery.getRequest());
        }
        if (delivery.getProperties().getReplyTo() != null) {
            return new ProcessIncomingRequest(delivery, queue);
        }
        return new ProcessIncomingBroadcast(delivery, queue);
    }

    //endregion

    //region Outgoing deliveries
    <T> void afterProcessingOutgoing(@Observes Outgoing<T> outgoing) throws IOException, InterruptedException {
        final ProcessOutgoingImpl<T> processingEvent = createProcessingEvent(outgoing);
        processOutgoingEvent.fire(processingEvent);

        if (processingEvent instanceof ProcessOutgoingBroadcast || processingEvent instanceof ProcessOutgoingResponse) {
            serializeFireAndForget(processingEvent);
        } else {
            serializeAndRpc((ProcessOutgoingRequest<?>) processingEvent);
        }
    }

    private <T> void serializeAndRpc(ProcessOutgoingRequest<T> processedEvent)
            throws IOException, InterruptedException {
        byte[] bytes = serialize(processedEvent);

        final Outgoing<T> outgoing = processedEvent.getOutgoing();
        final AMQP.BasicProperties properties = processedEvent.properties().build();
        final RpcClient.Response response = publisher.doRpc(outgoing.exchange(), processedEvent.routingKey(),
                                                            properties, bytes);

        final String queueName = properties.getReplyTo();

        this.responseDeliveryEvent.select(new FromQueue.Literal(queueName))
                .fireAsync(new ResponseDelivery<>(response.getEnvelope(), response.getProperties(), response.getBody(),
                                                  outgoing));
    }

    private void serializeFireAndForget(ProcessOutgoingImpl<?> processOutgoing)
            throws IOException, InterruptedException {
        final byte[] bytes = serialize(processOutgoing);


        publisher.doBroadcast(processOutgoing.exchange(), processOutgoing.routingKey(),
                              processOutgoing.properties().build(), bytes);
    }

    private <T> byte[] serialize(ProcessOutgoingImpl<T> processedEvent) throws IOException {
        Serializer<T> serializer = processedEvent.serializer;
        if (serializer == null) {
            serializer = serializationSelector.selectSerializer(processedEvent.content)
                    .orElseThrow(() -> new IllegalStateException("No Serializer applicable"));
        }
        try {
            serializer.serialize(processedEvent.content, processedEvent.properties(), processedEvent.body());
        } finally {
            processedEvent.body().close();
        }

        return processedEvent.finalBody.toByteArray();
    }

    private <T> ProcessOutgoingImpl<T> createProcessingEvent(Outgoing<T> outgoing) {
        final Optional<Delivery> incoming = messageMetaData.getIncomingDelivery();
        if (incoming.isPresent() && incoming.get().getProperties().getReplyTo() != null) {
            return new ProcessOutgoingResponse<>(outgoing, incoming.get());
        }
        if (outgoing.properties().getReplyTo() != null) {
            return new ProcessOutgoingRequest<>(outgoing);
        }
        return new ProcessOutgoingBroadcast<>(outgoing);
    }

    private static class ResponseDelivery<T> extends Delivery {
        final Outgoing<T> request;

        ResponseDelivery(Envelope envelope, AMQP.BasicProperties properties, byte[] body, Outgoing<T> request) {
            super(envelope, properties, body);
            this.request = request;
        }

        Outgoing<T> getRequest() {
            return request;
        }
    }

    //endregion
}
