package io.github.jhahn.enhancedcdi.messaging.impl;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Delivery;
import io.github.jhahn.enhancedcdi.messaging.PropertiesBuilder;
import io.github.jhahn.enhancedcdi.messaging.RpcEndpoint;
import io.github.jhahn.enhancedcdi.messaging.RpcNotActiveException;
import io.github.jhahn.enhancedcdi.messaging.processing.Outgoing;

import javax.annotation.Priority;
import javax.enterprise.context.ContextNotActiveException;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import java.util.Optional;

@Interceptor
@Priority(Interceptor.Priority.LIBRARY_BEFORE)
@RpcEndpoint
class RpcResultPublishingInterceptor {

    @Inject
    @io.github.jhahn.enhancedcdi.messaging.Outgoing
    PropertiesBuilder propertiesBuilder;
    @Inject
    Event<Outgoing<?>> outgoingDeliveryEvent;

    @Inject
    private MessageMetaData requestMetaData;

    @AroundInvoke
    Object publishReturnValue(InvocationContext invocationContext) throws Exception {
        final String replyTo = extractReplyToFromRequest();

        final Object result = invocationContext.proceed();

        publish(replyTo, result);
        return result;
    }

    private String extractReplyToFromRequest() {
        Optional<Delivery> request;
        try {
            request = requestMetaData.getIncomingDelivery();
        } catch (ContextNotActiveException | IllegalStateException ex) {
            throw new RpcNotActiveException("RPC method called outside of request scope", ex);
        }

        if (request.isEmpty()) {
            throw new RpcNotActiveException("RPC method called without RPC request in active request scope");
        }

        return request.map(Delivery::getProperties)
                .map(AMQP.BasicProperties::getReplyTo)
                .orElseThrow(() -> new RpcNotActiveException(
                        "RPC method called without RPC request in active request scope. "
                        + "Broadcast in active request scope does not have the 'replyTo' property."));
    }

    private <T> void publish(String replyTo, T result) {
        final Outgoing<T> outgoing = new Outgoing<>("", replyTo, propertiesBuilder.build(), result);
        outgoingDeliveryEvent.fire(outgoing);
    }
}
