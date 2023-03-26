package io.github.jhahnhro.enhancedcdi.messaging.rpc;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.interceptor.InterceptorBinding;

import io.github.jhahnhro.enhancedcdi.messaging.Incoming;

/**
 * An interceptor binding to mark {@link javax.enterprise.event.Observes event observer methods} for
 * {@link Incoming incoming RabbitMQ messages} as RPC endpoints. A corresponding interceptor is provided that
 * automatically publishes the result of the method.
 *
 * @apiNote Having multiple observer methods for the same event is, of course, allowed, but be aware that having more
 * than one of them annotated with {@code RpcEndpoint} will result in publishing multiple responses to the same request.
 * That is probably an error in most cases.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@InterceptorBinding
@Documented
public @interface RpcEndpoint {}
