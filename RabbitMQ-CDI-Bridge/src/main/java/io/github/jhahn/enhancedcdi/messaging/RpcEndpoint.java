package io.github.jhahn.enhancedcdi.messaging;

import javax.enterprise.util.Nonbinding;
import javax.interceptor.InterceptorBinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@InterceptorBinding
public @interface RpcEndpoint {
    /**
     * Set to {@code false} if the method should tolerate being called outside RPC contexts, e.g. if an observer method
     * for events coming in from RabbitMQ is declared whose observed type can come from both fire-and-forget-style
     * messages and rpc-style messages.
     *
     * @return {@code true} iff the RPC endpoint should throw an {@link RpcNotActiveException} when it is called outside
     * of request contexts belonging to an RPC requests.
     */
    @Nonbinding boolean throwOutsideOfRpcInvocation() default true;
}
