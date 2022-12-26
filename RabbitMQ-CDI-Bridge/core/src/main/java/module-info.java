import io.github.jhahnhro.enhancedcdi.messaging.impl.RabbitMqExtension;

open module enhancedcdi.rabbitmqCdiBridge {
    requires javax.inject;
    requires cdi.api;
    requires java.annotation;
    requires javax.interceptor.api;
    requires com.rabbitmq.client;
    requires enhancedcdi.core;

    exports io.github.jhahnhro.enhancedcdi.messaging;
    exports io.github.jhahnhro.enhancedcdi.messaging.messages;
    exports io.github.jhahnhro.enhancedcdi.messaging.processing;
    exports io.github.jhahnhro.enhancedcdi.messaging.rpc;
    exports io.github.jhahnhro.enhancedcdi.messaging.serialization;

    provides javax.enterprise.inject.spi.Extension with RabbitMqExtension;
}