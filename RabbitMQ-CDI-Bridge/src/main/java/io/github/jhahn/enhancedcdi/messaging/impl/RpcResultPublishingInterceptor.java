package io.github.jhahn.enhancedcdi.messaging.impl;

import com.rabbitmq.client.Delivery;
import io.github.jhahn.enhancedcdi.messaging.PublishTo;
import io.github.jhahn.enhancedcdi.messaging.RpcEndpoint;
import io.github.jhahn.enhancedcdi.messaging.RpcNotActiveException;
import io.github.jhahn.enhancedcdi.messaging.processing.Outgoing;

import javax.annotation.Priority;
import javax.enterprise.context.ContextNotActiveException;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.event.Event;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.DefinitionException;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import java.lang.reflect.Method;

@Interceptor
@Priority(Interceptor.Priority.LIBRARY_BEFORE)
@RpcEndpoint
public class RpcResultPublishingInterceptor {

    @Inject
    BeanManager beanManager;
    @Inject
    @io.github.jhahn.enhancedcdi.messaging.Outgoing
    PropertiesBuilderImpl propertiesBuilder;
    @Inject
    Event<Outgoing<?>> outgoingDeliveryEvent;

    @Inject
    private MessageMetaData requestMetaData;

    @AroundInvoke
    Object publishReturnValue(InvocationContext invocationContext) throws Exception {
        validatePreconditions(invocationContext);

        final Object result = invocationContext.proceed();

        publish(invocationContext, result);
        return result;
    }

    private void validatePreconditions(InvocationContext invocationContext) {
        final RpcEndpoint rpcEndpoint = invocationContext.getMethod().getAnnotation(RpcEndpoint.class);
        if (rpcEndpoint == null) {
            throw new DefinitionException("");
        }
        if (!rpcEndpoint.throwOutsideOfRpcInvocation()) {
            return;
        }

        try {
            beanManager.getContext(RequestScoped.class);
        } catch (ContextNotActiveException | IllegalArgumentException ex) {
            throw new RpcNotActiveException("RPC method called outside of request scope", ex);
        }

        Delivery request;
        try {
            request = requestMetaData.checkDelivery();
        } catch (IllegalStateException ex) {
            throw new RpcNotActiveException("RPC method called without RPC request in active request scope", ex);
        }

        if (request.getProperties().getReplyTo() == null) {
            throw new RpcNotActiveException("RPC method called without RPC request in active request scope. "
                                            + "Message in active request scope does not have the 'replyTo' property.");
        }
    }

    private Outgoing<?> getOutgoingDelivery(InvocationContext invocationContext, Object content) {
        final Method method = invocationContext.getMethod();
        final PublishTo publishTo = method.getAnnotation(PublishTo.class);
        final String exchange = publishTo.exchange();
        String routingKey = publishTo.routingKey();
        if (PublishTo.UNCONFIGURED_ROUTING_KEY.equals(routingKey)) {
            routingKey = "";
        }

        return new Outgoing<>(exchange, routingKey, propertiesBuilder.build(), content);
    }

    private void publish(InvocationContext invocationContext, Object result) {
        outgoingDeliveryEvent.fire(getOutgoingDelivery(invocationContext, result));
    }
}
