package io.github.jhahnhro.enhancedcdi.multiton.exception;

import jakarta.enterprise.inject.spi.DefinitionException;

public class MissingBeanParameterException extends DefinitionException {
    public MissingBeanParameterException(String message, Throwable t) {
        super(message, t);
    }

    public MissingBeanParameterException(String message) {
        super(message);
    }

    public MissingBeanParameterException(Throwable t) {
        super(t);
    }
}
