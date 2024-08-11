package io.github.jhahnhro.enhancedcdi.multiton.exception;

import jakarta.enterprise.inject.spi.DefinitionException;

public class InvalidParametrizedAnnotationException extends DefinitionException {
    public InvalidParametrizedAnnotationException(String message, Throwable t) {
        super(message, t);
    }

    public InvalidParametrizedAnnotationException(String message) {
        super(message);
    }

    public InvalidParametrizedAnnotationException(Throwable t) {
        super(t);
    }
}
