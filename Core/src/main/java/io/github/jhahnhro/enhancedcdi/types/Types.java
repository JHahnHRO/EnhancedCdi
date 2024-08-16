package io.github.jhahnhro.enhancedcdi.types;

import java.lang.invoke.MethodType;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import io.github.jhahnhro.enhancedcdi.util.Iteration;

/**
 * General helper methods for dealing with the Java Type system.
 */
public final class Types {

    private Types() {}

    public static Class<?> erasure(Type type) {
        return switch (type) {
            case Class<?> clazz -> clazz;
            case ParameterizedType parameterizedType -> (Class<?>) parameterizedType.getRawType();
            case GenericArrayType genericArrayType -> erasure(genericArrayType.getGenericComponentType()).arrayType();
            case TypeVariable<?> typeVariable -> erasure(typeVariable.getBounds()[0]);
            default -> throw new IllegalArgumentException();
        };
    }

    /**
     * Returns the set of all direct and indirect {@link Class#getSuperclass() super-classes} of the given class,
     * including {@code Object.class} and the given class itself if it is indeed a class and not an interface. The
     * returned list is ordered by assignability; it begins with {@code clazz} and ends with {@code Object.class}.
     *
     * @param clazz a Class
     * @param <T>   the type
     * @return the super-classes
     */
    public static <T> List<Class<?>> superClasses(Class<T> clazz) {
        if (clazz.isInterface()) {
            return List.of(Object.class);
        }
        return Stream.<Class<?>>iterate(clazz, Objects::nonNull, Class::getSuperclass).toList();
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
        final Function<Class<?>, Stream<Class<?>>> edges = aClass -> {
            final Class<?> superclass = aClass.getSuperclass();
            final Stream<Class<?>> interfaces = Arrays.stream(aClass.getInterfaces());
            return superclass == null ? interfaces : Stream.concat(Stream.of(superclass), interfaces);
        };
        final List<Class<?>> superInterfaces = Iteration.breadthFirstSearch(clazz, edges)
                .stream()
                .filter(Class::isInterface)
                .toList();
        return Collections.unmodifiableSequencedSet(new LinkedHashSet<>(superInterfaces));
    }

    /**
     * Convenience method to capture a {@link TypeVariable} by name from the context surrounding the caller. Only the
     * immediate surroundings are considered:
     * <ul>
     *     <li>If this method is called from a static initializer, a type variable of the class will be captured.</li>
     *     <li>If this method is called from a constructor, a type variable of the constructor will be captured.</li>
     *     <li>If this method is called from another method, a type variable of the method will be captured.</li>
     *     </li>
     * </ul>
     *
     * @param name the name of the type variable that should be captured.
     * @return the type variable of the given name.
     * @throws NoSuchElementException if there is no type variable with the given name.
     */
    @SuppressWarnings("java:S1452") // Sonar does not like returning wildcards, but here it is necessary
    public static TypeVariable<?> captureTypeVariable(String name) {
        final GenericDeclaration callingMethod = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(stackFrames -> stackFrames.dropWhile(f -> f.getClassName().equals(Types.class.getName()))
                        .map(Types::reflectMethod)
                        .findFirst()
                        .orElseThrow());

        for (TypeVariable<?> typeParameter : callingMethod.getTypeParameters()) {
            if (typeParameter.getName().equals(name)) {
                return typeParameter;
            }
        }
        throw new NoSuchElementException("No type variable with name " + name + " on " + callingMethod);
    }

    private static GenericDeclaration reflectMethod(StackWalker.StackFrame stackFrame) {
        final Class<?> declaringClass = stackFrame.getDeclaringClass();
        final MethodType methodType = stackFrame.getMethodType();
        final String methodName = stackFrame.getMethodName();

        try {
            return switch (methodName) {
                case "<clinit>" -> declaringClass;
                case "<init>" -> declaringClass.getConstructor(methodType.parameterArray());
                default -> declaringClass.getDeclaredMethod(methodName, methodType.parameterArray());
            };
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }
}
