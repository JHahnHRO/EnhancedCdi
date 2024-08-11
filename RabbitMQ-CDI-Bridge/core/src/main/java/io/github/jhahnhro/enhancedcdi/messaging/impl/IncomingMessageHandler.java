package io.github.jhahnhro.enhancedcdi.messaging.impl;

import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.WARNING;

import java.io.IOException;
import java.lang.annotation.Annotation;

import com.rabbitmq.client.Envelope;
import io.github.jhahnhro.enhancedcdi.messaging.FromExchange;
import io.github.jhahnhro.enhancedcdi.messaging.FromQueue;
import io.github.jhahnhro.enhancedcdi.messaging.Publisher;
import io.github.jhahnhro.enhancedcdi.messaging.RabbitMqApplicationException;
import io.github.jhahnhro.enhancedcdi.messaging.Redelivered;
import io.github.jhahnhro.enhancedcdi.messaging.WithRoutingKey;
import io.github.jhahnhro.enhancedcdi.messaging.impl.producers.MessageMetaDataProducer;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Acknowledgement;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.DeserializationException;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.SerializationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.ObserverException;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

/**
 * Handles incoming raw messages by de-serializing them into java objects and fire those as CDI events. Also deals with
 * missing message acknowledgement and exceptions in the event observers.
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

    @Inject
    Publisher publisher;

    @Inject
    ExceptionMapping exceptionMapping;

    public void handleDelivery(@ObservesAsync InternalDelivery incomingDelivery) {
        final Incoming<byte[]> rawMessage = incomingDelivery.rawMessage();
        final Acknowledgement acknowledgement = incomingDelivery.ack();

        try {
            // make sure request metadata is available in the current request scope for injection into event observers
            metaData.setDelivery(rawMessage.queue(), rawMessage.envelope(), rawMessage.properties(), acknowledgement);

            Incoming<?> message = serialization.deserialize(rawMessage);
            metaData.setMessage(message);

            fireEvent(message);

            acknowledgeIfNecessary(acknowledgement);
        } catch (DeserializationException e) {
            rejectIfNecessary(acknowledgement, e);
        } catch (RuntimeException e) {
            handleException(incomingDelivery, e);
        }
    }

    private void acknowledgeIfNecessary(Acknowledgement acknowledgement) {
        if (acknowledgement.getState() == Acknowledgement.State.UNACKNOWLEDGED) {
            LOG.log(WARNING, "Incoming delivery in manual acknowledge mode was not explicitly acknowledged. "
                             + "That is probably an error. It will be acknowledged now.");
            try {
                acknowledgement.ack();
            } catch (IOException ex) {
                LOG.log(ERROR, "Incoming delivery could not be acknowledged.", ex);
            }
        }
    }

    private void handleException(final InternalDelivery incomingDelivery, Exception e) {
        if (e instanceof ObserverException) {
            e = (Exception) e.getCause();
        }

        if (incomingDelivery.rawMessage() instanceof Incoming.Request<byte[]> request) {
            if (e instanceof RabbitMqApplicationException applicationException) {
                acknowledgeAndRespond(applicationException.getResponse(), incomingDelivery.ack());
                return;
            }

            final Outgoing.Response<byte[], Object> response = exceptionMapping.applyExceptionMapper(request, e);
            if (response != null) {
                acknowledgeAndRespond(response, incomingDelivery.ack());
                return;
            }
        }

        rejectIfNecessary(incomingDelivery.ack(), e);
    }

    private void acknowledgeAndRespond(final Outgoing.Response<?, ?> response, final Acknowledgement acknowledgement) {
        acknowledgeIfNecessary(acknowledgement);
        try {
            publisher.publish(response);
        } catch (SerializationException | IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.log(ERROR, "Response could not be published", e);
        }
    }

    private void rejectIfNecessary(Acknowledgement acknowledgement, Throwable e) {
        if (acknowledgement.getState() == Acknowledgement.State.UNACKNOWLEDGED) {
            LOG.log(ERROR, "Could not handle incoming delivery. It will be rejected now without re-queueing it.", e);
            try {
                acknowledgement.reject(false);
            } catch (IOException ex) {
                LOG.log(ERROR, "Incoming delivery could not be rejected.", ex);
            }
        } else {
            LOG.log(ERROR, "Could not handle incoming delivery.", e);
        }
    }

    private void fireEvent(Incoming<?> message) {
        processedEvent.select(getStandardQualifiers(message.envelope(), message.queue())).fire(message.content());
    }

    private Annotation[] getStandardQualifiers(Envelope messageEnvelope, String queueName) {
        return new Annotation[]{new FromQueue.Literal(queueName), new FromExchange.Literal(
                messageEnvelope.getExchange()), new WithRoutingKey.Literal(
                messageEnvelope.getRoutingKey()), Redelivered.Literal.of(messageEnvelope.isRedeliver())};
    }
}
