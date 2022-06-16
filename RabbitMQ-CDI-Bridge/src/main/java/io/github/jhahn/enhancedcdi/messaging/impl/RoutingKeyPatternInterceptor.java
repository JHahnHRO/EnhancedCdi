package io.github.jhahn.enhancedcdi.messaging.impl;

import io.github.jhahn.enhancedcdi.messaging.RoutingKeyPattern;

import javax.annotation.Priority;
import javax.enterprise.inject.Intercepted;
import javax.enterprise.inject.spi.Bean;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Interceptor
// Prevent PublishingInterceptors from publishing the possible null result
@Priority(Interceptor.Priority.LIBRARY_BEFORE - 1)
@RoutingKeyPattern("")
public class RoutingKeyPatternInterceptor {

    @Inject
    MessageMetaData messageMetaData;
    @Inject
    RoutingKeyPatternCache routingKeyPatternCache;

    private final Pattern classLevelPattern;


    @Inject
    RoutingKeyPatternInterceptor(@Intercepted Bean<?> interceptedBean) {

        final RoutingKeyPattern annotation = interceptedBean.getBeanClass().getAnnotation(RoutingKeyPattern.class);
        if (annotation != null) {
            this.classLevelPattern = Pattern.compile(annotation.value());
        } else {
            this.classLevelPattern = null;
        }
    }

    @AroundInvoke
    Object checkRoutingKeyPattern(InvocationContext invocationContext) throws Exception {
        messageMetaData.checkDelivery();

        final String routingKey = messageMetaData.routingKey();
        final Pattern pattern = routingKeyPatternCache.get(invocationContext.getMethod()).orElse(classLevelPattern);

        final Matcher matcher = pattern.matcher(routingKey);
        if (matcher.matches()) {
            return invocationContext.proceed();
        } else {
            return null;
        }
    }
}
