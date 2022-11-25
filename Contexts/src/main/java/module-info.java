open module enhancedcdi.contexts {
    requires javax.inject;
    requires cdi.api;
    requires enhancedcdi.core;
    requires java.annotation;
    requires javax.interceptor.api;

    exports io.github.jhahnhro.enhancedcdi.context;
    exports io.github.jhahnhro.enhancedcdi.context.extension;
}