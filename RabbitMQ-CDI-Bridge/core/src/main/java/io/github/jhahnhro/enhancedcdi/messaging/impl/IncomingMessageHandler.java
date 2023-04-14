package io.github.jhahnhro.enhancedcdi.messaging.impl;

import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.WARNING;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.Optional;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.event.ObserverException;
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
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;
import io.github.jhahnhro.enhancedcdi.messaging.rpc.RpcException;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.RabbitMqApplicationException;

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

    @Inject
    Event<Outgoing<Object>> responseEvent;

    @Inject
    ExceptionMapping exceptionMapping;

    public void handleDelivery(@ObservesAsync InternalDelivery incomingDelivery) {
        final Incoming<byte[]> rawMessage = incomingDelivery.rawMessage();
        final Acknowledgment acknowledgment = incomingDelivery.ack();

        try {
            // make sure request metadata is available in the current request scope for injection into event observers
            metaData.setDelivery(rawMessage.delivery(), rawMessage.queue(), acknowledgment);

            Incoming<?> message = serialization.deserialize(rawMessage);
            metaData.setMessage(message);

            fireEvent(message);

            acknowledgeIfNecessary(acknowledgment);
        } catch (RpcException e) {
            throw e;
        } catch (Exception e) {
            handleException(incomingDelivery, e);
        }
    }

    private void acknowledgeIfNecessary(Acknowledgment acknowledgment) {
        if (acknowledgment.getState() == Acknowledgment.State.UNACKNOWLEDGED) {
            LOG.log(WARNING, "Incoming delivery in manual acknowledge mode was not explicitly acknowledged. "
                             + "That is probably an error. It will be acknowledged now.");
            try {
                acknowledgment.ack();
            } catch (IOException ex) {
                LOG.log(ERROR, "Incoming delivery could not be acknowledged.", ex);
            }
        }
    }

    private void handleException(final InternalDelivery incomingDelivery, Throwable e) {
        if (e instanceof ObserverException) {
            e = e.getCause();
        }

        if (incomingDelivery.rawMessage() instanceof Incoming.Request<byte[]>) {
            if (e instanceof RabbitMqApplicationException applicationException) {
                acknowledgeAndRespond(incomingDelivery, applicationException.getResponse());
                return;
            }

            final Optional<Outgoing.Response<byte[], Object>> response = exceptionMapping.applyExceptionMapper(e);
            if (response.isPresent()) {
                acknowledgeAndRespond(incomingDelivery, response.get());
                return;
            }
        }

        rejectIfNecessary(incomingDelivery.ack(), e);
    }

    private void acknowledgeAndRespond(InternalDelivery incomingDelivery,
                                       final Outgoing.Response<byte[], Object> response) {
        acknowledgeIfNecessary(incomingDelivery.ack());
        responseEvent.fireAsync(response);
    }

    private void rejectIfNecessary(Acknowledgment acknowledgment, Throwable e) {
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
