package io.github.jhahnhro.enhancedcdi.multiton;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A bean class annotated as a parametrized bean will lead to multiple copies of that bean, one for each enum constant
 * in the given enum type, see {@link ParametrizedBeanExtension}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ParametrizedBean {
}
