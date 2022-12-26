package io.github.jhahnhro.enhancedcdi.messaging.rpc;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.enterprise.util.Nonbinding;
import javax.interceptor.InterceptorBinding;

import com.rabbitmq.client.BasicProperties;
import io.github.jhahnhro.enhancedcdi.messaging.Incoming;

/**
 * An interceptor binding to mark {@link javax.enterprise.event.Observes event observer methods} for
 * {@link Incoming incoming RabbitMQ messages} as RPC endpoints. A corresponding
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
