package io.github.jhahnhro.enhancedcdi.metadata;

import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class Normalizer {

    // container annotation -> contained annotation with Annotation.class as a placeholder for classes that are not
    // container annotations
    private final Map<Class<? extends Annotation>, Class<? extends Annotation>> containedClasses = new HashMap<>();
    // container annotation -> value() method
    private final Map<Class<? extends Annotation>, Method> valueMethods = new HashMap<>();

    /**
     * If {@code ann} is a container annotation of some {@link Repeatable} annotation, then this method returns a stream
     * of the repeated annotations inside that container. In all other cases it returns a one-element stream consisting
     * of the input.
     *
     * @param ann an annotation
     * @return A stream either consisting only of the input, or the contained annotations if {@code ann} is the
     * container annotation of some {@code @Repeatable} annotation.
     */
    public Stream<Annotation> normalize(Annotation ann) {
        Class<? extends Annotation> containedClass = containedClasses.computeIfAbsent(ann.annotationType(),
                                                                                      this::getContainedAnnotationClass);
        if (containedClass.equals(Annotation.class)) {
            return Stream.of(ann);
        }

        try {
            Method method = valueMethods.get(ann.annotationType());
            assert method != null; // we looked up the value method when we populated the containedClasses map

            return Arrays.stream((Annotation[]) method.invoke(ann));
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private Class<? extends Annotation> getContainedAnnotationClass(Class<? extends Annotation> containerClass) {
        Optional<Class<? extends Annotation>> clazz = Optional.ofNullable(getValueMethod(containerClass))
                .map(Method::getReturnType)
                .map(Class::getComponentType)
                .filter(Class::isAnnotation)
                .map(cls -> cls.asSubclass(Annotation.class));

        if (clazz.isPresent()) {
            Repeatable repeatable = clazz.get().getAnnotation(Repeatable.class);
            if (repeatable != null && repeatable.value().equals(containerClass)) {
                return clazz.get();
            }
        }
        // not a container => store a placeholder value so that we don't redo all that computation next time
        return Annotation.class;
    }

    private Method getValueMethod(Class<? extends Annotation> containerType) {
        if (containerType == null) {
            return null;
        } else {
            return this.valueMethods.computeIfAbsent(containerType, containerClass -> {
                try {
                    return containerClass.getMethod("value");
                } catch (NoSuchMethodException e) {
                    return null;
                }
            });
        }
    }
    //endregion

}
