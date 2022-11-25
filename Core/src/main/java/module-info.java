open module enhancedcdi.core {
    requires javax.inject;
    requires cdi.api;
    requires java.annotation;
    requires javax.interceptor.api;
    requires microprofile.config.api;

    exports io.github.jhahnhro.enhancedcdi.pooled;
    exports io.github.jhahnhro.enhancedcdi.metadata;
    exports io.github.jhahnhro.enhancedcdi.types;
    exports io.github.jhahnhro.enhancedcdi.util to enhancedcdi.contexts, enhancedcdi.rabbitmqCdiBridge;
}