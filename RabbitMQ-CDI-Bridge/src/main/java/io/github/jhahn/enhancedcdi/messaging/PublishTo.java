package io.github.jhahn.enhancedcdi.messaging;

import javax.enterprise.util.Nonbinding;
import javax.interceptor.InterceptorBinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@InterceptorBinding
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface PublishTo {
    String UNCONFIGURED_ROUTING_KEY = "PublishTo.routingKey.UNCONFIGURED";

    @Nonbinding String exchange();

    @Nonbinding String routingKey() default UNCONFIGURED_ROUTING_KEY;
}
