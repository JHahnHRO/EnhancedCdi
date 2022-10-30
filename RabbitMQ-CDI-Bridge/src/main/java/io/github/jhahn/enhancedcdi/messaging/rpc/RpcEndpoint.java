package io.github.jhahn.enhancedcdi.messaging.rpc;

import com.rabbitmq.client.BasicProperties;

import javax.enterprise.util.Nonbinding;
import javax.interceptor.InterceptorBinding;
import java.lang.annotation.*;

/**
 * An interceptor binding to mark {@link javax.enterprise.event.Observes event observer methods} for
 * {@link io.github.jhahn.enhancedcdi.messaging.Incoming incoming RabbitMQ messages} as RPC endpoints. A corresponding
 * interceptor is provided that automatically publishes the result of the method.
 *
 * @apiNote Having multiple observer methods for the same event is, of course, allowed, but be aware that having more
 * than one of them annotated with {@code RpcEndpoint} will result in publishing multiple responses to the same request.
 * That is probably an error in most cases.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@InterceptorBinding
@Documented
public @interface RpcEndpoint {
    /**
     * How an invocation of the intercepted method should be handled if it does not originate from an incoming rabbitmq
     * message (that also is a request, i.e. has its {@link BasicProperties#getReplyTo() replyTo property} set to a
     * non-null value).
     */
    @Nonbinding NonRpcInvocation nonRpcInvocations() default NonRpcInvocation.PROCEED;

    enum NonRpcInvocation {
        /**
         * Throw a {@link RpcNotActiveException} on non-rpc invocations
         */
        THROW,
        /**
         * Simply invokes the method, returning whatever the method returns. Does not publish the result to the broker.
         */
        PROCEED,
        /**
         * Do NOT invoke the RpcEndpoint method and returns with the default value of the method's return type instead,
         * i.e. 0 for primitive types and {@code null} for reference types.
         */
        DO_NOT_PROCEED
    }
}
