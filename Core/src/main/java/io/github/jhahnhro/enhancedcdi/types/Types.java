package io.github.jhahnhro.enhancedcdi.types;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import io.github.jhahnhro.enhancedcdi.util.Iteration;

/**
 * General helper methods for dealing with the Java Type system.
 */
public final class Types {

    private Types() {}

    public static Class<?> erasure(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        } else if (type instanceof ParameterizedType parameterizedType) {
            return (Class<?>) parameterizedType.getRawType();
        } else if (type instanceof GenericArrayType genericArrayType) {
            return erasure(genericArrayType.getGenericComponentType()).arrayType();
        } else if (type instanceof TypeVariable<?> typeVariable) {
            return erasure(typeVariable.getBounds()[0]);
        } else {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Returns the set of all direct and indirect {@link Class#getSuperclass() super-classes} of the given class,
     * including {@code Object.class} and the given class itself if it is indeed a class and not an interface. The
     * returned set has a fixed iteration order given by assignability; it begins with {@code clazz} and ends with
     * {@code Object.class}.
     *
     * @param clazz a Class
     * @param <T>   the type
     * @return the set of super-classes
     */
    public static <T> Set<Class<?>> superClasses(Class<T> clazz) {
        if (clazz.isInterface()) {
            return Set.of(Object.class);
        }

        Set<Class<?>> result = new LinkedHashSet<>();
        for (Class<?> superClass = clazz; superClass != null; superClass = superClass.getSuperclass()) {
            result.add(superClass);
        }

        return Collections.unmodifiableSet(result);
    }

    /**
     * Returns the set of all direct and indirect {@link Class#getInterfaces() super-interfaces} of the given class,
     * including the class itself if it is an interface itself. The returned set has a fixed iteration order. Interfaces
     * "closer" in the type hierarchy to {@code clazz} are earlier in the returned set.
     *
     * @param clazz a Class
     * @param <T>   the type
     * @return the set of super-interfaces
     */
    public static <T> Set<Class<?>> superInterfaces(Class<T> clazz) {
        Set<Class<?>> result = Iteration.breadthFirstSearch(clazz, aClass -> Arrays.stream(aClass.getInterfaces()));
        if (!clazz.isInterface()) {
            result = new LinkedHashSet<>(result);
            result.remove(clazz);
            result = Collections.unmodifiableSet(result);
        }

        return result;
    }

}
