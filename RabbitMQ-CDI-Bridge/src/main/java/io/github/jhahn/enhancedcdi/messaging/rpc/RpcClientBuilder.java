package io.github.jhahn.enhancedcdi.messaging.rpc;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BasicProperties;
import io.github.jhahn.enhancedcdi.messaging.RoutingKey;
import io.github.jhahn.enhancedcdi.messaging.messages.Outgoing;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class RpcClientBuilder {

    private String exchange;
    private String routingKey;
    private BasicProperties defaultProperties;
    private Supplier<String> correlationIdSupplier;

    private static <T> Optional<T> getParameterValueIf(Method method, Object[] args, Predicate<Parameter> predicate) {
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            final Parameter parameter = parameters[i];

            if (predicate.test(parameter)) {
                return Optional.of((T) args[i]);
            }
        }
        return Optional.empty();
    }

    public RpcClientBuilder setExchange(String exchange) {
        this.exchange = exchange;
        return this;
    }

    public RpcClientBuilder setRoutingKey(String routingKey) {
        this.routingKey = routingKey;
        return this;
    }

    public RpcClientBuilder setDefaultProperties(BasicProperties properties) {
        this.defaultProperties = properties;
        return this;
    }

    public RpcClientBuilder setCorrelationIdSupplier(Supplier<String> correlationIdSupplier) {
        this.correlationIdSupplier = correlationIdSupplier;
        return this;
    }

    public <T> T build(Class<T> interfaceClass) {
        return build(interfaceClass, interfaceClass.getClassLoader());
    }

    public <T> T build(Class<T> interfaceClass, final ClassLoader classLoader) {
        if (!interfaceClass.isInterface()) {
            throw new IllegalArgumentException("Given class is not an interface");
        }

        return (T) Proxy.newProxyInstance(classLoader, new Class[]{interfaceClass, AutoCloseable.class},
                                          buildInvocationHandler(interfaceClass));
    }

    private InvocationHandler buildInvocationHandler(Class<?> interfaceClass) {
        return new RpcClientInvocationHandler(interfaceClass, exchange, routingKey, defaultProperties,
                                              correlationIdSupplier);
    }

    private static class RpcClientInvocationHandler implements InvocationHandler {

        private final Class<?> interfaceClass;
        private final String exchange;
        private final String routingKey;
        private final BasicProperties defaultProperties;

        private RpcClientInvocationHandler(Class<?> interfaceClass, final String exchange, String routingKey,
                                           BasicProperties defaultProperties, Supplier<String> correlationIdSupplier) {
            this.interfaceClass = interfaceClass;
            this.exchange = exchange;
            this.routingKey = routingKey;
            this.defaultProperties = defaultProperties;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }

            Object content = null;
            final String exchange = this.exchange;
            String routingKey = getRoutingKey(method, args);
            AMQP.BasicProperties properties = buildProperties(method, args);
            new Outgoing.Request<>(exchange, routingKey, properties, content);
            return null;
        }

        private String getRoutingKey(Method method, Object[] args) {
            return getRoutingKeyFromParameterAnnotation(method, args)//
                    .or(() -> getRoutingKeyFromParameterNames(method, args))
                    .or(() -> getRoutingKeyFromMethodAnnotation(method))
                    .or(this::getRoutingKeyFromClassAnnotation)
                    .orElseThrow(() -> new IllegalStateException("No routing key."));
        }

        private Optional<String> getRoutingKeyFromParameterAnnotation(Method method, Object[] args) {
            return getParameterValueIf(method, args, param -> param.isAnnotationPresent(RoutingKey.class));
        }

        private Optional<String> getRoutingKeyFromParameterNames(Method method, Object[] args) {
            return getParameterValueIf(method, args, param -> param.isNamePresent() && "routingKey".equalsIgnoreCase(
                    param.getName()) && String.class == param.getType());
        }

        private Optional<String> getRoutingKeyFromMethodAnnotation(Method method) {
            return Optional.ofNullable(method.getAnnotation(WithRoutingKey.class)).map(WithRoutingKey::value);
        }

        private Optional<String> getRoutingKeyFromClassAnnotation() {
            return Optional.ofNullable(interfaceClass.getAnnotation(WithRoutingKey.class)).map(WithRoutingKey::value);
        }

        private AMQP.BasicProperties buildProperties(Method method, Object[] args) {
            return null;
        }

        private Optional<AMQP.BasicProperties> getPropertiesFromParameterName(Method method, Object[] args) {
            return getParameterValueIf(method, args, param -> param.isNamePresent() && "properties".equalsIgnoreCase(
                    param.getName()));
        }

    }
}
