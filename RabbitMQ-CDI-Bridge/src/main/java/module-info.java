module enhancedcdi.rabbitmqCdiBridge {
    requires javax.inject;
    requires cdi.api;
    requires java.annotation;
    requires javax.interceptor.api;
    requires com.rabbitmq.client;
    requires com.google.protobuf;
    requires enhancedcdi.core;

    exports io.github.jhahn.enhancedcdi.messaging;
    exports io.github.jhahn.enhancedcdi.messaging.processing;
    exports io.github.jhahn.enhancedcdi.messaging.serialization;
    exports io.github.jhahn.enhancedcdi.messaging.rpc;

    provides javax.enterprise.inject.spi.Extension with io.github.jhahn.enhancedcdi.messaging.impl.RabbitMqExtension;
}