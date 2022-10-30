package io.github.jhahn.enhancedcdi.messaging.impl;

import io.github.jhahn.enhancedcdi.messaging.Publisher;
import io.github.jhahn.enhancedcdi.messaging.messages.Outgoing;
import io.github.jhahn.enhancedcdi.messaging.messages.OutgoingMessageBuilder;
import io.github.jhahn.enhancedcdi.messaging.rpc.RpcEndpoint;
import io.github.jhahn.enhancedcdi.messaging.rpc.RpcNotActiveException;

import javax.annotation.Priority;
import javax.enterprise.context.ContextNotActiveException;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Map;

import static java.util.Map.entry;

@Interceptor
@Priority(Interceptor.Priority.LIBRARY_BEFORE)
@RpcEndpoint
class RpcResultPublishingInterceptor {

    private static final Map<Class<?>, Object> DEFAULT_VALUES = Map.ofEntries(entry(boolean.class, false),
                                                                              entry(char.class, '\0'),
                                                                              entry(byte.class, (byte) 0),
                                                                              entry(short.class, (short) 0),
                                                                              entry(int.class, 0),
                                                                              entry(long.class, 0L),
                                                                              entry(float.class, 0.0f),
                                                                              entry(double.class, 0.0d));

    // Instance because OutgoingMessageBuilder is @Dependent and we need lazy lookup
    @Inject
    Instance<OutgoingMessageBuilder<?, ?>> outgoingMessageBuilderInstance;

    @Inject
    Publisher publisher;

    @AroundInvoke
    Object publishReturnValue(InvocationContext invocationContext) throws Exception {
        OutgoingMessageBuilder<?, ?> responseBuilder = isRpcRequestContext();
        if (responseBuilder == null) {
            return handleNonRpcInvocation(invocationContext);
        } else {
            return handleRpcInvocation(invocationContext, responseBuilder);
        }
    }

    private OutgoingMessageBuilder<?, ?> isRpcRequestContext() {
        try {
            return outgoingMessageBuilderInstance.get();
        } catch (ContextNotActiveException ex) {
            return null;
        }
    }


    private Object handleNonRpcInvocation(InvocationContext invocationContext) throws Exception {
        final Method method = invocationContext.getMethod();
        final RpcEndpoint binding = method.getAnnotation(RpcEndpoint.class);

        return switch (binding.nonRpcInvocations()) {
            case THROW -> throw new RpcNotActiveException(
                    method + " was invoked outside of a RequestScope of an RabbitMQ RPC request.");
            case PROCEED -> invocationContext.proceed();
            case DO_NOT_PROCEED -> DEFAULT_VALUES.get(method.getReturnType());
        };
    }

    private Object handleRpcInvocation(InvocationContext invocationContext,
                                       OutgoingMessageBuilder<?, ?> responseBuilder)
            throws Exception {

        Object result = invocationContext.proceed();

        final Outgoing<?> response = responseBuilder.setContent(result).build();
        final Type responseType = invocationContext.getMethod().getGenericReturnType();
        publisher.send(response, responseType);

        return result;
    }
}