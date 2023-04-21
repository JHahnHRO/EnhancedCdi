module enhancedcdi.rabbitmqcdibridge.json {
    requires java.annotation;
    requires java.json.bind;

    requires enhancedcdi.rabbitmqcdibridge.core;
    requires com.rabbitmq.client;

    exports io.github.jhahnhro.enhancedcdi.serialization.json;
}