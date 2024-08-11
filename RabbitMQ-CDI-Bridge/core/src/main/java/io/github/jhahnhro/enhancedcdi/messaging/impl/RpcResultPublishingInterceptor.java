package io.github.jhahnhro.enhancedcdi.messaging.impl;

import static java.lang.System.Logger.Level.WARNING;

import java.lang.reflect.Method;
import java.lang.reflect.Type;

import io.github.jhahnhro.enhancedcdi.messaging.Publisher;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing.Response;
import io.github.jhahnhro.enhancedcdi.messaging.rpc.RpcEndpoint;
import io.github.jhahnhro.enhancedcdi.messaging.rpc.RpcException;
import io.github.jhahnhro.enhancedcdi.messaging.rpc.RpcNotActiveException;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

@Interceptor
@Priority(Interceptor.Priority.LIBRARY_BEFORE)
@RpcEndpoint
class RpcResultPublishingInterceptor {

    // Instance because Builder is @Dependent and we need lazy lookup
    @Inject
    Instance<Response.Builder<?, ?>> responseBuilderInstance;

    @Inject
    Publisher publisher;

    @AroundInvoke
    public Object publishReturnValue(InvocationContext invocationContext) throws Exception {
        try {
            return handleRpcInvocation(invocationContext);
        } catch (ContextNotActiveException | RpcNotActiveException e) {
            logNonRpcInvocationWarning(invocationContext);
            return invocationContext.proceed();
        }
    }

    private void logNonRpcInvocationWarning(InvocationContext invocationContext) {
        final Method method = invocationContext.getMethod();
        final System.Logger logger = System.getLogger(method.getDeclaringClass().getName());

        final String msg = "%s is annotated with @RpcEndpoint, but was just called outside of an RPC request scope. "
                           + "That is probably a bug. No response will be published to the RabbitMQ broker.";
        logger.log(WARNING, msg.formatted(method), new RpcNotActiveException());
    }


    private Object handleRpcInvocation(InvocationContext invocationContext) throws Exception {
        Response.Builder<?, ?> responseBuilder = responseBuilderInstance.get();

        Object result = invocationContext.proceed();

        publisher.publish(getResponse(invocationContext.getMethod(), responseBuilder, result));

        return result;
    }

    private Response<?, ?> getResponse(Method method, Response.Builder<?, ?> responseBuilder, Object result) {
        if (Response.class == method.getReturnType()) {
            return useResultDirectly(method, responseBuilder.getRequest(), (Response<?, ?>) result);
        } else {
            return transformResult(method, responseBuilder, result);
        }
    }

    private Response<?, ?> transformResult(Method method, Response.Builder<?, ?> responseBuilder, Object result) {
        final Type genericResponseType = method.getGenericReturnType();
        return responseBuilder.setType(genericResponseType).setContent(result).build();
    }

    private Response<?, ?> useResultDirectly(Method method, Incoming.Request<?> request,
                                             final Response<?, ?> response) {
        if (response == null) {
            throw new RpcException("The RPC endpoint %s returned null".formatted(method));
        }
        if (!response.request().equals(request)) {
            throw new RpcException(
                    ("The RPC endpoint %s returned a response to a different request than the one in the currently "
                     + "active request scope (%s)").formatted(method, request));
        }
        return response;
    }
}