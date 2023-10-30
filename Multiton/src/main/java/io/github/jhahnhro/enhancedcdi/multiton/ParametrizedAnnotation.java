package io.github.jhahnhro.enhancedcdi.multiton;

import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import javax.enterprise.util.AnnotationLiteral;

/**
 * In a {@link ParametrizedBean parametrized bean}, annotations on the class itself, its fields, constructors, methods
 * and their parameters can be declared as parameter-dependent with this annotation.
 * <p>
 * The container will automatically instantiate {@link AnnotationLiteral literals} of the provided annotation type for
 * each enum constant of the type of the {@link BeanParameter} field.
 * <ul>
 *     <li>First the container will look for a class for annotation literals. This class can be defined in the optional
 *     {@link #literalType()} attribute. It the attribute is not specified, it will be assumed that the annotation type
 *     has an inner class named "Literal".</li>
 *     <li>The literal class must extend from {@code AnnotationLiteral} and implement the given annotation type.</li>
 *     <li>The literal class is then searched for ways to instantiate it from an enum constant:
 *     <ul>
 *         <li>If the literal class has a public constructor with a single argument to which the enum class is
 *         assignable,
 *         then this constructor is used.</li>
 *         <li>If no such constructor can be found, the public static methods declared in the literal class will be
 *         searched for one which accept a single argument to which the enum class is assignable and with the literal
 *         class itself or one of its subclasses as return type.
 *         If a method named "of" exists, it will be used. If no such method with the right signature exists, all
 *         other public
 *         static methods in the literal class will be tried and if a unique such method with a matching signature
 *         exists, it will be used. If no such methods or more than one exist, a definition error will be raised.</li>
 *         <li>The constructor/method so found must be
 *         {@link java.lang.reflect.Constructor#setAccessible(boolean) accessible} from this module, otherwise a
 *          definition error is raised.</li>
 *     </ul></li>
 *     <li>If no class satisfying these conditions is found, a definition error will be raised during container
 *     startup.</li>
 *     <li>The constructor/factory method will then be invoked for each of the constants in the enum class. If any of
 *     these return {@code null} or throw an exception, a definition error will be raised.</li>
 * </ul>
 * <p>
 * It is possible to have multiple constructors and/or factory methods for different enum classes so that the same
 * annotation type can be used in differently parametrized beans. The container will only call constructors/methods with
 * argument type compatible to the enum class of the parametrized bean. In particular: A public static method
 * accepting something high up in the type hierarchy like {@code Object} will be considered for all enum classes.
 * <p>
 * The container is not required to instantiate a new literal every time it encounters a parametrized annotation and can
 * reuse the annotation literals it produced before.
 */
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ParametrizedAnnotation.List.class)
public @interface ParametrizedAnnotation {
    /**
     * An annotation type that can be parametrized by an enum.
     *
     * @return A qualifier annotation type.
     */
    Class<? extends Annotation> value();

    /**
     * Optional. A class whose instances are literals of the annotation type in {@link #value()}. If a non-default value
     * is specified, the given class must both extend {@link AnnotationLiteral} and implement the annotation type in
     * {@link #value()}.
     *
     * @return A class of annotation literals.
     */
    Class<? extends AnnotationLiteral> literalType() default AnnotationLiteral.class;

    @Retention(RetentionPolicy.RUNTIME)
    @interface List {
        ParametrizedAnnotation[] value();
    }

    final class Literal<A extends Annotation, L extends AnnotationLiteral<A>>
            extends AnnotationLiteral<ParametrizedAnnotation> implements ParametrizedAnnotation {

        private final Class<A> annotationClass;
        private final Class<L> literalType;

        private Literal(Class<A> annotationClass, Class<L> literalType) {
            this.annotationClass = annotationClass;
            this.literalType = literalType;
        }

        public Literal(Class<A> annotationClass) {
            this(annotationClass, (Class) AnnotationLiteral.class);
        }

        @Override
        public Class<A> value() {
            return annotationClass;
        }

        @Override
        public Class<L> literalType() {
            return literalType;
        }
    }
}
