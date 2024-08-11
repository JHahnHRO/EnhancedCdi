module enhancedcdi.rabbitmqcdibridge.json {
    requires jakarta.json.bind;
    requires jakarta.annotation;

    requires enhancedcdi.rabbitmqcdibridge.core;
    requires com.rabbitmq.client;

    exports io.github.jhahnhro.enhancedcdi.serialization.json;
}