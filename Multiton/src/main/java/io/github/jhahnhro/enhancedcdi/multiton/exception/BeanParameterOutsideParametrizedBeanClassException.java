package io.github.jhahnhro.enhancedcdi.multiton.exception;

import io.github.jhahnhro.enhancedcdi.multiton.BeanParameter;
import io.github.jhahnhro.enhancedcdi.multiton.ParametrizedBean;

public class BeanParameterOutsideParametrizedBeanClassException extends InvalidBeanParameterException {

    private static final String MESSAGE = BeanParameter.class.getCanonicalName() + " qualified injection points"
                                          + "are only allowed inside bean classes with the " + ParametrizedBean.class
                                                  .getCanonicalName() + " annotation";

    public BeanParameterOutsideParametrizedBeanClassException() {
        super(MESSAGE);
    }

    public BeanParameterOutsideParametrizedBeanClassException(Throwable t) {
        super(MESSAGE, t);
    }
}
