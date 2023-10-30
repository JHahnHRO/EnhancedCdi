package io.github.jhahnhro.enhancedcdi.multiton.exception;

import javax.enterprise.inject.spi.DefinitionException;

public class InvalidBeanParameterException extends DefinitionException {
    public InvalidBeanParameterException(String message, Throwable t) {
        super(message, t);
    }

    public InvalidBeanParameterException(String message) {
        super(message);
    }

    public InvalidBeanParameterException(Throwable t) {
        super(t);
    }
}
