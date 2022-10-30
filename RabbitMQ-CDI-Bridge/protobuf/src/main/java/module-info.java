module enhancedcdi.rabbitmqCdiBridge.protobuf {
    requires javax.inject;
    requires cdi.api;
    requires com.rabbitmq.client;
    requires com.google.protobuf;

    requires enhancedcdi.rabbitmqCdiBridge;

    exports io.github.jhahnhro.enhancedcdi.serialization.protobuf;
}