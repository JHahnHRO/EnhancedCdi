package io.github.jhahnhro.enhancedcdi.messaging.impl;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Map;
import javax.annotation.Priority;
import javax.enterprise.context.ContextNotActiveException;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

import io.github.jhahnhro.enhancedcdi.messaging.Publisher;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;
import io.github.jhahnhro.enhancedcdi.messaging.rpc.RpcEndpoint;
import io.github.jhahnhro.enhancedcdi.messaging.rpc.RpcNotActiveException;

@Interceptor
@Priority(Interceptor.Priority.LIBRARY_BEFORE)
@RpcEndpoint
class RpcResultPublishingInterceptor {

    private static final Map<Class<?>, Object> DEFAULT_VALUES = Map.ofEntries(Map.entry(boolean.class, false),
                                                                              Map.entry(char.class, '\0'),
                                                                              Map.entry(byte.class, (byte) 0),
                                                                              Map.entry(short.class, (short) 0),
                                                                              Map.entry(int.class, 0),
                                                                              Map.entry(long.class, 0L),
                                                                              Map.entry(float.class, 0.0f),
                                                                              Map.entry(double.class, 0.0d));

    // Instance because Builder is @Dependent and we need lazy lookup
    @Inject
    Instance<Outgoing.Response.Builder<?, ?>> responseBuilderInstance;

    @Inject
    Publisher publisher;

    @AroundInvoke
    Object publishReturnValue(InvocationContext invocationContext) throws Exception {
        Outgoing.Response.Builder<?, ?> responseBuilder = isRpcRequestContext();
        if (responseBuilder == null) {
            return handleNonRpcInvocation(invocationContext);
        } else {
            return handleRpcInvocation(invocationContext, responseBuilder);
        }
    }

    private Outgoing.Response.Builder<?, ?> isRpcRequestContext() {
        try {
            return responseBuilderInstance.get();
        } catch (ContextNotActiveException ex) {
            return null;
        }
    }


    private Object handleNonRpcInvocation(InvocationContext invocationContext) throws Exception {
        final Method method = invocationContext.getMethod();
        final RpcEndpoint binding = method.getAnnotation(RpcEndpoint.class);

        return switch (binding.nonRpcInvocations()) {
            case THROW -> throw new RpcNotActiveException(
                    method + " was invoked outside of the RequestScope of a RabbitMQ RPC request.");
            case PROCEED -> invocationContext.proceed();
            case DO_NOT_PROCEED -> DEFAULT_VALUES.get(method.getReturnType());
        };
    }

    private Object handleRpcInvocation(InvocationContext invocationContext,
                                       Outgoing.Response.Builder<?, ?> responseBuilder)
            throws Exception {

        Object result = invocationContext.proceed();

        final Type responseType = invocationContext.getMethod().getGenericReturnType();
        final Outgoing<?> response = responseBuilder.setContent(result).setType(responseType).build();
        publisher.send(response);

        return result;
    }
}