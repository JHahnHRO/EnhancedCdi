package io.github.jhahnhro.enhancedcdi.multiton.exception;

public class AnnotationFactoryNotFoundException extends InvalidParametrizedAnnotationException {
    public AnnotationFactoryNotFoundException(String message, Throwable t) {
        super(message, t);
    }

    public AnnotationFactoryNotFoundException(String message) {
        super(message);
    }

    public AnnotationFactoryNotFoundException(Throwable t) {
        super(t);
    }
}
