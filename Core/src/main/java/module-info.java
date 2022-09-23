module enhancedcdi.core {
    requires javax.inject;
    requires cdi.api;
    requires java.annotation;
    requires javax.interceptor.api;
    requires microprofile.config.api;

    exports io.github.jhahnhro.enhancedcdi.pooled;
    exports io.github.jhahnhro.enhancedcdi.metadata;
}