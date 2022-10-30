module enhancedcdi.rabbitmqCdiBridge.json {
    requires java.annotation;
    requires java.json.bind;

    requires enhancedcdi.rabbitmqCdiBridge;
    requires com.rabbitmq.client;

    exports io.github.jhahnhro.enhancedcdi.serialization.json;
}