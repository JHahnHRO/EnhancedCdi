package io.github.jhahnhro.enhancedcdi.multiton;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Qualifier;

/**
 * The concrete enum constant corresponding to the copy of a {@link ParametrizedBean parametrized bean} can be injected
 * in a field of such beans with the {@link BeanParameter} annotation.
 * <pre>{@code
 *  enum Color{ RED, GREEN, BLUE }
 *
 *  @ParametrizedBean
 *  public class MyBean {
 *  @BeanParameter
 *  private Color myColor;
 *  }
 *  }</pre>
 * <p>
 * Such a field must be a non-static, non-final and its type must be an enum class.
 * <p>
 * At most one bean parameter field is allowed. If the container encounters more than one, it will treat this as a
 * definition error.
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface BeanParameter {

    class Literal extends AnnotationLiteral<BeanParameter> implements BeanParameter {
        public static final Literal INSTANCE = new Literal();

        private Literal() {}
    }
}
